package com.wingedsheep.gameserver.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Always-mounted client bootstrap config. Lets the web client learn which optional subsystems the
 * server actually runs, so it can hide UI for features that aren't available.
 *
 * Unlike [AuthController] / [AccountDeckController] / [AccountStatsController], this is deliberately
 * NOT gated on `accounts.enabled` — that's the whole point: the client needs an answer even when
 * accounts are off, otherwise it shows a magic-link sign-in form that can only fail (the request
 * falls through to the SPA resource handler and 404s/405s).
 *
 *  - GET /api/config → { accountsEnabled }
 */
@RestController
@RequestMapping("/api/config")
class ConfigController(
    @Value("\${accounts.enabled:false}") private val accountsEnabled: Boolean,
) {
    data class AppConfig(val accountsEnabled: Boolean)

    @GetMapping
    fun config(): AppConfig = AppConfig(accountsEnabled)
}
