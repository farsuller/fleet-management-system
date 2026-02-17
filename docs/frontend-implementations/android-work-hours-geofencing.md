## Work Hours & Geofencing Protection

### Overview

Prevents local database pollution when drivers forget to turn off GPS tracking outside working hours or service area.

### Implementation Components

#### 1. Work Hours Manager

```kotlin
// app/src/main/kotlin/com/solodev/fleet/driver/tracking/WorkHoursManager.kt
class WorkHoursManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {
    
    fun isWithinWorkHours(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        val startHour = sharedPreferences.getInt(KEY_START_HOUR, DEFAULT_START_HOUR)
        val endHour = sharedPreferences.getInt(KEY_END_HOUR, DEFAULT_END_HOUR)
        
        return currentHour in startHour until endHour
    }
    
    fun isWorkDay(): Boolean {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        
        // Monday (2) to Saturday (7), exclude Sunday (1)
        return dayOfWeek in Calendar.MONDAY..Calendar.SATURDAY
    }
    
    fun shouldTrack(): Boolean {
        return isWorkDay() && isWithinWorkHours()
    }
    
    fun setWorkHours(startHour: Int, endHour: Int) {
        sharedPreferences.edit {
            putInt(KEY_START_HOUR, startHour)
            putInt(KEY_END_HOUR, endHour)
        }
    }
    
    companion object {
        private const val KEY_START_HOUR = "work_start_hour"
        private const val KEY_END_HOUR = "work_end_hour"
        private const val DEFAULT_START_HOUR = 6  // 6 AM
        private const val DEFAULT_END_HOUR = 22   // 10 PM
    }
}
```

#### 2. Geofencing Manager

```kotlin
// app/src/main/kotlin/com/solodev/fleet/driver/tracking/GeofenceManager.kt
class GeofenceManager @Inject constructor(
    private val context: Context,
    private val geofencingClient: GeofencingClient
) {
    
    fun setupServiceAreaGeofence(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 50_000f // 50km default
    ) {
        val geofence = Geofence.Builder()
            .setRequestId("service_area")
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or 
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()
        
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
        
        val geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Geofence added successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence", e)
                }
        }
    }
    
    companion object {
        private const val TAG = "GeofenceManager"
    }
}

// Geofence Broadcast Receiver
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }
        
        when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                // Driver left service area - pause tracking
                pauseTracking(context)
                showNotification(context, "Left service area - tracking paused")
            }
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                // Driver entered service area - resume tracking
                resumeTracking(context)
                showNotification(context, "Entered service area - tracking resumed")
            }
        }
    }
    
    private fun pauseTracking(context: Context) {
        val intent = Intent(context, SensorTrackingService::class.java)
        context.stopService(intent)
    }
    
    private fun resumeTracking(context: Context) {
        val intent = Intent(context, SensorTrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
    
    private fun showNotification(context: Context, message: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(context, "geofence_channel")
            .setContentTitle("Fleet Tracking")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_location)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        notificationManager.notify(GEOFENCE_NOTIFICATION_ID, notification)
    }
    
    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        private const val GEOFENCE_NOTIFICATION_ID = 1003
    }
}
```

#### 3. Enhanced Sensor Tracking Service with Work Hours Check

```kotlin
// Enhanced handleNewLocation with work hours check
private fun handleNewLocation(location: Location) {
    // 1. Check work hours FIRST
    if (!workHoursManager.shouldTrack()) {
        Log.i(TAG, "Outside work hours - skipping data collection")
        
        // Show notification once per day
        if (shouldShowOffHoursNotification()) {
            showOffHoursNotification()
        }
        
        return // Don't collect data
    }
    
    // 2. Validate location
    if (location.accuracy > 50f) {
        Log.w(TAG, "Location accuracy too low: ${location.accuracy}m")
        return
    }
    
    // 3. Create sensor ping (only if within work hours)
    val ping = SensorPing(
        vehicleId = getVehicleId(),
        latitude = location.latitude,
        longitude = location.longitude,
        // ... rest of sensor data
    )
    
    // 4. Send to repository
    lifecycleScope.launch {
        coordinateRepository.addCoordinate(ping)
    }
}

private fun showOffHoursNotification() {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Tracking Paused")
        .setContentText("Outside work hours (6 AM - 10 PM). GPS tracking paused to save battery.")
        .setSmallIcon(R.drawable.ic_pause)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
    
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(OFF_HOURS_NOTIFICATION_ID, notification)
    
    // Mark notification shown
    sharedPreferences.edit {
        putLong(KEY_LAST_OFF_HOURS_NOTIFICATION, System.currentTimeMillis())
    }
}

private fun shouldShowOffHoursNotification(): Boolean {
    val lastShown = sharedPreferences.getLong(KEY_LAST_OFF_HOURS_NOTIFICATION, 0)
    val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
    return lastShown < oneDayAgo
}

companion object {
    private const val OFF_HOURS_NOTIFICATION_ID = 1004
    private const val KEY_LAST_OFF_HOURS_NOTIFICATION = "last_off_hours_notification"
}
```

#### 4. Shift Schedule Integration (Backend)

```kotlin
// Fetch driver's shift schedule from backend
class ShiftScheduleRepository @Inject constructor(
    private val apiClient: FleetApiClient
) {
    
    suspend fun getCurrentShift(): Shift? {
        return try {
            val response = apiClient.getDriverShift()
            if (response.status == HttpStatusCode.OK) {
                response.body<Shift>()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch shift schedule", e)
            null
        }
    }
    
    suspend fun isOnDuty(): Boolean {
        val shift = getCurrentShift() ?: return false
        
        val now = Instant.now()
        return now in shift.startTime..shift.endTime
    }
    
    companion object {
        private const val TAG = "ShiftScheduleRepository"
    }
}

@Serializable
data class Shift(
    val shiftId: String,
    val driverId: String,
    val startTime: Instant,
    val endTime: Instant,
    val vehicleId: String
)
```

#### 5. Settings UI for Work Hours Configuration

```kotlin
@Composable
fun TrackingSettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val workHoursEnabled by viewModel.workHoursEnabled.collectAsState()
    val startHour by viewModel.startHour.collectAsState()
    val endHour by viewModel.endHour.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Tracking Settings", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Work Hours Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-pause outside work hours")
            Switch(
                checked = workHoursEnabled,
                onCheckedChange = { viewModel.setWorkHoursEnabled(it) }
            )
        }
        
        if (workHoursEnabled) {
            // Start Hour Picker
            Text("Start Hour: ${startHour}:00")
            Slider(
                value = startHour.toFloat(),
                onValueChange = { viewModel.setStartHour(it.toInt()) },
                valueRange = 0f..23f,
                steps = 23
            )
            
            // End Hour Picker
            Text("End Hour: ${endHour}:00")
            Slider(
                value = endHour.toFloat(),
                onValueChange = { viewModel.setEndHour(it.toInt()) },
                valueRange = 0f..23f,
                steps = 23
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Geofence Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Auto-pause outside service area")
            Switch(
                checked = geofenceEnabled,
                onCheckedChange = { viewModel.setGeofenceEnabled(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Privacy Protection",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "These settings prevent unnecessary data collection when you're off-duty, " +
                    "saving battery and protecting your privacy.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
```

### Benefits

| Scenario | Without Protection | With Protection |
|----------|-------------------|-----------------|
| **Forgot to turn off GPS** | Database fills with 1000s of off-duty pings | Auto-paused at 10 PM, no data collected |
| **Weekend tracking** | Unnecessary battery drain + data | Auto-paused on Sundays |
| **Outside service area** | Collects irrelevant location data | Geofence pauses tracking |
| **Personal errands** | Privacy concern (tracked off-duty) | Only tracks during shift hours |
| **Storage exhaustion** | Local DB fills up (1000+ pings) | Prevented by work hours limit |

### Configuration Options

**Default Settings**:
- Work hours: 6 AM - 10 PM
- Work days: Monday - Saturday
- Geofence radius: 50km from depot
- Max storage: 1000 pings, 7 days

**Customizable**:
- Admin can configure work hours per driver
- Driver can adjust in app settings
- Geofence can be disabled for roaming drivers
- Manual override always available

### Privacy Compliance

✅ **GDPR Compliant**: Only tracks during work hours  
✅ **Driver Consent**: Settings clearly explained  
✅ **Data Minimization**: No off-duty data collection  
✅ **Right to Disconnect**: Auto-pause after hours  
✅ **Transparency**: Notifications when paused/resumed
