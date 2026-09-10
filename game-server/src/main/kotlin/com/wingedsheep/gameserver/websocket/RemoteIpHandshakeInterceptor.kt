package com.wingedsheep.gameserver.websocket

import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

/**
 * Captures the connecting client's IP at the WebSocket handshake and stashes it in the session
 * attributes under [CLIENT_IP_ATTR], where [ConnectionHandler] reads it onto the [PlayerIdentity].
 *
 * Behind a reverse proxy the real client IP is in `X-Forwarded-For` (first hop); we fall back to the
 * socket's remote address for direct connections. The raw value is used only for an admin-only
 * geolocation estimate and is never sent to clients.
 */
@Component
class RemoteIpHandshakeInterceptor : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val forwarded = request.headers.getFirst("X-Forwarded-For")
            ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val ip = forwarded ?: request.remoteAddress.address?.hostAddress
        if (ip != null) attributes[CLIENT_IP_ATTR] = ip
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit

    companion object {
        const val CLIENT_IP_ATTR = "clientIp"
    }
}
