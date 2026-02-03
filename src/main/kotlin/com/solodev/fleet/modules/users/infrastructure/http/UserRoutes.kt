package com.solodev.fleet.modules.users.infrastructure.http

import com.solodev.fleet.modules.domain.ports.UserRepository
import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.application.dto.UserResponse
import com.solodev.fleet.modules.users.application.usecases.GetUserProfileUseCase
import com.solodev.fleet.modules.users.application.usecases.RegisterUserUseCase
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(repository: UserRepository) {
    val registerUseCase = RegisterUserUseCase(repository)
    val getProfileUseCase = GetUserProfileUseCase(repository)

    route("/v1/users") {
        post("/register") {
            val request = call.receive<UserRegistrationRequest>()
            val user = registerUseCase.execute(request)
            call.respond(ApiResponse.success(UserResponse.fromDomain(user), call.requestId))
        }

        get("/me") {
            // Placeholder: In a real app, get the userId from the Auth Session/Token
            // For demonstration, we'll use a hardcoded or query param ID if provided
            val userId = call.parameters["userId"]

            if (userId == null) {
                call.respond(ApiResponse.error("UNAUTHORIZED", "Login required", call.requestId))
                return@get
            }

            val user = getProfileUseCase.execute(userId)
            if (user != null) {
                call.respond(ApiResponse.success(UserResponse.fromDomain(user), call.requestId))
            } else {
                call.respond(ApiResponse.error("NOT_FOUND", "User not found", call.requestId))
            }
        }
    }
}
