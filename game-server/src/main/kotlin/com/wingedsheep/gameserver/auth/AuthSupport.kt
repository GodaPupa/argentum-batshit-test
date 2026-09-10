package com.wingedsheep.gameserver.auth

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Resolves the current account from the `Authorization: Bearer <token>` header on REST requests.
 * Token verification is stateless (HMAC) — no database lookup — so this is cheap to call per request.
 */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class AuthSupport(private val authTokenService: AuthTokenService) {

    /** Returns the verified claims, or throws 401 if the header is missing/invalid/expired. */
    fun requireUser(authorizationHeader: String?): AuthClaims =
        userOrNull(authorizationHeader)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign-in required")

    /** Returns the verified claims, or null if the header is missing/invalid/expired. */
    fun userOrNull(authorizationHeader: String?): AuthClaims? {
        val token = authorizationHeader
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return authTokenService.verify(token)
    }
}
