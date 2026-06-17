package com.remotedroid.input

import com.remotedroid.protocol.ClientMessage
import com.remotedroid.protocol.ServerMessage

/**
 * The boundary between the server and the Accessibility Service. The server produces
 * commands; the service applies them to the system. This keeps the Ktor layer
 * independent of Android APIs.
 */
interface CommandExecutor {
    fun execute(msg: ClientMessage)
    fun welcome(): ServerMessage.Welcome
}
