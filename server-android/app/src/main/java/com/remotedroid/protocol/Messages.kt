package com.remotedroid.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Messages from the remote. The JSON `type` discriminator matches the PWA `messages.ts` exactly.
 */
@Serializable
sealed interface ClientMessage {
    @Serializable @SerialName("hello")
    data class Hello(val token: String, val client: String, val v: Int) : ClientMessage

    @Serializable @SerialName("move")
    data class Move(val dx: Double, val dy: Double) : ClientMessage

    @Serializable @SerialName("tap")
    data object Tap : ClientMessage

    @Serializable @SerialName("longpress")
    data object LongPress : ClientMessage

    @Serializable @SerialName("dragstart")
    data object DragStart : ClientMessage

    @Serializable @SerialName("dragmove")
    data class DragMove(val dx: Double, val dy: Double) : ClientMessage

    @Serializable @SerialName("dragend")
    data object DragEnd : ClientMessage

    @Serializable @SerialName("scroll")
    data class Scroll(val dx: Double, val dy: Double) : ClientMessage

    @Serializable @SerialName("volume")
    data class Volume(val dir: String) : ClientMessage

    @Serializable @SerialName("mute")
    data object Mute : ClientMessage

    @Serializable @SerialName("text")
    data class Text(val value: String) : ClientMessage

    @Serializable @SerialName("submit")
    data object Submit : ClientMessage

    @Serializable @SerialName("backspace")
    data object Backspace : ClientMessage

    @Serializable @SerialName("clear")
    data object Clear : ClientMessage

    @Serializable @SerialName("global")
    data class Global(val action: String) : ClientMessage

    @Serializable @SerialName("ping")
    data object Ping : ClientMessage
}

@Serializable
data class Screen(val w: Int, val h: Int)

@Serializable
data class Features(val imeEnter: Boolean, val scroll: Boolean)

/**
 * Messages from the server to the remote.
 */
@Serializable
sealed interface ServerMessage {
    @Serializable @SerialName("welcome")
    data class Welcome(val screen: Screen, val android: Int, val features: Features) : ServerMessage

    @Serializable @SerialName("error")
    data class ErrorMsg(val code: String, val message: String? = null) : ServerMessage

    @Serializable @SerialName("status")
    data class Status(val accessibility: Boolean? = null, val focus: Boolean? = null) : ServerMessage

    @Serializable @SerialName("pong")
    data object Pong : ServerMessage
}

val RdJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun decodeClient(text: String): ClientMessage = RdJson.decodeFromString(text)

fun encodeServer(msg: ServerMessage): String = RdJson.encodeToString(msg)
