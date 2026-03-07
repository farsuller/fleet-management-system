package com.solodev.fleet.modules.tracking.infrastructure.websocket

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStateDelta
import com.solodev.fleet.modules.tracking.application.dto.diff
import com.solodev.fleet.modules.tracking.application.dto.full
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.models.PaginationParams
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis-backed delta broadcaster for distributed deployments.
 *
 * Features:
 * - Delta encoding for bandwidth efficiency
 * - Session management across multiple instances
 * - Redis Pub/Sub for cross-node broadcasting (uses JedisPool for thread safety)
 * - Automatic state synchronization via Redis cache
 *
 * Thread safety: Each publish borrows a short-lived connection from the pool.
 * The subscriber gets a dedicated long-lived connection that stays in subscribe mode.
 */
open class RedisDeltaBroadcaster(
    private val redisCache: RedisCacheManager,
    private val vehicleRepository: VehicleRepository,
    private val jedisPool: JedisPool? = null
) {
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisorJob)

    companion object {
        private const val REDIS_CHANNEL = "fleet_vehicle_updates"
    }

    init {
        // Subscribe to Redis Pub/Sub for cross-node updates if Redis is available
        if (jedisPool != null) {
            subscribeToRedisUpdates()
        }
    }

    suspend fun broadcastIfChanged(vehicleId: UUID, newState: VehicleRouteState) {
        val redisKey = "vehicle_state:$vehicleId"
        val lastState = redisCache.getOrSet<VehicleRouteState?>(redisKey, 3600) { null }

        val delta = if (lastState == null) {
            VehicleStateDelta.full(newState)
        } else {
            VehicleStateDelta.diff(lastState, newState)
        }

        if (delta.hasChanges()) {
            val message = Json.encodeToString(delta)

            // Broadcast to local sessions
            sessions.values.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    // Session may be closed, silently ignore
                }
            }

            // Publish to Redis Pub/Sub for cross-node distribution
            publishToRedis(message)

            // Update cache for next comparison
            redisCache.getOrSet(redisKey, 3600) { newState }
        }
    }

    suspend fun addSession(sessionId: String, session: DefaultWebSocketServerSession) {
        sessions[sessionId] = session
        sendInitialState(session)
    }

    private suspend fun sendInitialState(session: DefaultWebSocketServerSession) {
        val (activeVehicles, _) = vehicleRepository.findAll(PaginationParams(limit = 100, cursor = null))
        activeVehicles.forEach { vehicle ->
            val state = redisCache.getOrSet<VehicleRouteState>("vehicle_state:${vehicle.id}", 3600) { null }
            if (state != null) {
                try {
                    val delta = VehicleStateDelta.full(state)
                    session.send(Frame.Text(Json.encodeToString(delta)))
                } catch (e: Exception) {
                    // Session closed, ignore
                }
            }
        }
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * Publish delta to Redis Pub/Sub for cross-node distribution.
     * Borrows a short-lived connection from the pool for each publish call,
     * avoiding conflicts with the long-lived subscriber connection.
     */
    private fun publishToRedis(message: String) {
        try {
            jedisPool?.resource?.use { conn ->
                conn.publish(REDIS_CHANNEL, message)
            }
        } catch (e: Exception) {
            println("Failed to publish to Redis: ${e.message}")
        }
    }

    /**
     * Subscribe to Redis Pub/Sub channel for updates from other backend nodes.
     * Gets a dedicated long-lived connection from the pool that stays in subscribe mode.
     * This connection is separate from the ones used for publish/cache operations.
     */
    private fun subscribeToRedisUpdates() {
        scope.launch {
            try {
                val subscriberConn = jedisPool?.resource
                if (subscriberConn != null) {
                    try {
                        subscriberConn.subscribe(object : JedisPubSub() {
                            override fun onMessage(channel: String, message: String) {
                                // Broadcast message from another node to local sessions
                                scope.launch {
                                    sessions.values.forEach { session ->
                                        try {
                                            session.send(Frame.Text(message))
                                        } catch (e: Exception) {
                                            // Session closed, ignore
                                        }
                                    }
                                }
                            }

                            override fun onSubscribe(channel: String, subscribedChannels: Int) {
                                println("Redis Pub/Sub subscribed to channel: $channel")
                            }
                        }, REDIS_CHANNEL)
                    } finally {
                        subscriberConn.close()
                    }
                }
            } catch (e: Exception) {
                println("Redis Pub/Sub subscription failed: ${e.message}")
            }
        }
    }

    fun shutdown() {
        supervisorJob.cancel()
        sessions.clear()
    }
}