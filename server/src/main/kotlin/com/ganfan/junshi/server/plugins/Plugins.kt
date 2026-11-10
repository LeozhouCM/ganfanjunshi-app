package com.ganfan.junshi.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ganfan.junshi.server.model.UserDto
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

val jwtSecret = System.getenv("JWT_SECRET") ?: "ganfan-junshi-secret-key-2024"
val jwtIssuer = "ganfan-junshi"
val jwtAudience = "ganfan-app"

fun Application.configureSerialization() {
    install(ContentNegotiation) { gson() }
}

fun Application.configureHTTP() {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("code" to -1, "message" to (cause.message ?: "error")))
        }
    }
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asLong()
                if (userId != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}

fun generateToken(user: UserDto): String {
    return JWT.create()
        .withAudience(jwtAudience)
        .withIssuer(jwtIssuer)
        .withClaim("userId", user.id)
        .withClaim("phone", user.phone)
        .sign(Algorithm.HMAC256(jwtSecret))
}

fun ApplicationCall.userId(): Long? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asLong()
}
