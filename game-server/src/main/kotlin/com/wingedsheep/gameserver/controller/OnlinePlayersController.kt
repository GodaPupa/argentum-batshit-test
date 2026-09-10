package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.session.SessionRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/players")
class OnlinePlayersController(
    private val sessionRegistry: SessionRegistry
) {

    data class OnlinePlayersDTO(val count: Int)

    @GetMapping("/online")
    fun online(): ResponseEntity<OnlinePlayersDTO> {
        val count = sessionRegistry.getAllIdentities()
            .count { !it.isAi && it.webSocketSession?.isOpen == true }
        return ResponseEntity.ok(OnlinePlayersDTO(count))
    }
}
