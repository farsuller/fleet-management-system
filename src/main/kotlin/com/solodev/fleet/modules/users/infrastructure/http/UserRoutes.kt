package com.solodev.fleet.modules.users.infrastructure.http

import com.solodev.fleet.modules.users.application.dto.*
import com.solodev.fleet.modules.users.application.usecases.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import com.solodev.fleet.shared.utils.JwtService
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(
    userRepository: UserRepository,
    tokenRepository: VerificationTokenRepository,
    jwtService: JwtService
) {
    val registerUserUseCase = RegisterUserUseCase(userRepository, tokenRepository)
    val verifyEmailUseCase = VerifyEmailUseCase(userRepository, tokenRepository)
    val getUserProfileUseCase = GetUserProfileUseCase(userRepository)
    val updateUserUseCase = UpdateUserUseCase(userRepository)
    val deleteUserUseCase = DeleteUserUseCase(userRepository)
    val listUsersUseCase = ListUsersUseCase(userRepository)
    val listRolesUseCase = ListRolesUseCase(userRepository)
    val assignRoleUseCase = AssignRoleUseCase(userRepository)
    val loginUserUseCase = LoginUserUseCase(userRepository, jwtService)

    route("/v1/auth/verify") {
        get {
            val token =
                call.request.queryParameters["token"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error(
                            "MISSING_TOKEN",
                            "Token required",
                            call.requestId
                        )
                    )

            try {
                verifyEmailUseCase.execute(token)
                call.respond(
                    ApiResponse.success(
                        mapOf("message" to "Email successfully verified"),
                        call.requestId
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error(
                        "VERIFICATION_FAILED",
                        e.message ?: "Invalid token",
                        call.requestId
                    )
                )
            }
        }
    }

    route("/v1/users") {

        post("/register") {
            try {
                val request = call.receive<UserRegistrationRequest>()
                val user = registerUserUseCase.execute(request)
                call.respond(HttpStatusCode.Created, ApiResponse.success(UserResponse.fromDomain(user), call.requestId))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "VALIDATION_ERROR",
                        e.message ?: "Invalid data",
                        call.requestId
                    )
                )
            } catch (e: IllegalStateException) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse.error(
                        "CONFLICT",
                        e.message ?: "Resource conflict",
                        call.requestId
                    )
                )
            }
        }

        // Login
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val (user, token) = loginUserUseCase.execute(request)

                val response = LoginResponse(token = token, user = UserResponse.fromDomain(user))
                call.respond(ApiResponse.success(response, call.requestId))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiResponse.error(
                        "AUTH_FAILED",
                        e.message ?: "Invalid credentials",
                        call.requestId
                    )
                )
            }
        }

        authenticate("auth-jwt") {
            get {
                val users = listUsersUseCase.execute()
                call.respond(ApiResponse.success(users.map { UserResponse.fromDomain(it) }, call.requestId))
            }

            get("/roles") {
                val roles = listRolesUseCase.execute()
                call.respond(
                    ApiResponse.success(roles.map { RoleResponse.fromDomain(it) }, call.requestId)
                )
            }

            route("/{id}") {
                get {
                    val id =
                        call.parameters["id"]
                            ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "ID required",
                                    call.requestId
                                )
                            )
                    val user = getUserProfileUseCase.execute(id)
                    user?.let {
                        call.respond(ApiResponse.success(UserResponse.fromDomain(it), call.requestId))
                    }
                        ?: call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "User not found", call.requestId)
                        )
                }

                patch {
                    try {
                        val id =
                            call.parameters["id"]
                                ?: return@patch call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error(
                                        "MISSING_ID",
                                        "ID required",
                                        call.requestId
                                    )
                                )
                        val request = call.receive<UserUpdateRequest>()
                        val updated = updateUserUseCase.execute(id, request)
                        updated?.let {
                            call.respond(
                                ApiResponse.success(UserResponse.fromDomain(it), call.requestId)
                            )
                        }
                            ?: call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "User not found", call.requestId)
                            )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ApiResponse.error(
                                "VALIDATION_ERROR",
                                e.message ?: "Invalid data",
                                call.requestId
                            )
                        )
                    }
                }

                delete {
                    val id =
                        call.parameters["id"]
                            ?: return@delete call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "ID required",
                                    call.requestId
                                )
                            )
                    val deleted = deleteUserUseCase.execute(id)
                    if (deleted) {
                        call.respond(
                            HttpStatusCode.OK,
                            ApiResponse.success(
                                mapOf("message" to "User deleted successfully"),
                                call.requestId
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "User not found", call.requestId)
                        )
                    }
                }

                post("/roles") {
                    val id =
                        call.parameters["id"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "MISSING_ID",
                                    "ID required",
                                    call.requestId
                                )
                            )
                    val roleName =
                        call.receive<Map<String, String>>()["roleName"]
                            ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "INVALID_BODY",
                                    "roleName required",
                                    call.requestId
                                )
                            )

                    try {
                        val updated = assignRoleUseCase.execute(id, roleName)
                        updated?.let {
                            call.respond(
                                ApiResponse.success(UserResponse.fromDomain(it), call.requestId)
                            )
                        }
                            ?: call.respond(
                                HttpStatusCode.NotFound,
                                ApiResponse.error("NOT_FOUND", "User not found", call.requestId)
                            )
                    } catch (e: IllegalArgumentException) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error(
                                "ROLE_NOT_FOUND",
                                e.message ?: "Role not found",
                                call.requestId
                            )
                        )
                    }
                }
            }
        }
    }
}
