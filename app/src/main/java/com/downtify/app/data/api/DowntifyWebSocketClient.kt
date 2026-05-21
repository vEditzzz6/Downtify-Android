package com.downtify.app.data.api

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class DowntifyWebSocketClient(
    serverUrl: String,
    private val clientId: String,
    private val onMessage: (String) -> Unit,
    private val onOpen: () -> Unit = {},
    private val onClose: () -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) : WebSocketClient(URI("$serverUrl/api/ws?client_id=$clientId")) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        onOpen()
    }

    override fun onMessage(message: String?) {
        message?.let { onMessage(it) }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        onClose()
    }

    override fun onError(ex: Exception?) {
        ex?.let { onError(it) }
    }
}
