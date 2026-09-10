package com.wingedsheep.gameserver.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the opt-in accounts / persistence / magic-link-auth subsystem.
 *
 * Everything here is dormant unless [enabled] is true (which also requires the DataSource + Flyway
 * auto-config to be un-excluded — see application.yml). When disabled, none of the account beans
 * load and the server behaves exactly as before: anonymous, in-memory, no database.
 */
@ConfigurationProperties(prefix = "accounts")
data class AccountsProperties(
    val enabled: Boolean = false,
    val auth: AuthProperties = AuthProperties(),
)

data class AuthProperties(
    /** HMAC-SHA256 secret for signing auth tokens. Blank => a random secret is generated at startup. */
    val secret: String = "",
    /** Lifetime of an issued auth token. */
    val tokenTtlHours: Long = 720,
    /** Lifetime of a single-use magic-link login token. */
    val loginTokenTtlMinutes: Long = 15,
    /** Public base URL the magic link points at (the web client's origin). */
    val baseUrl: String = "http://localhost:5173",
    /** From address used on the magic-link email. */
    val fromEmail: String = "no-reply@wingedsheep.com",
)
