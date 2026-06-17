package com.remotedroid

import com.remotedroid.protocol.ClientMessage
import com.remotedroid.protocol.Features
import com.remotedroid.protocol.Screen
import com.remotedroid.protocol.ServerMessage
import com.remotedroid.protocol.decodeClient
import com.remotedroid.protocol.encodeServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {
    @Test
    fun decodesMove() {
        assertEquals(ClientMessage.Move(12.0, -3.0), decodeClient("""{"type":"move","dx":12,"dy":-3}"""))
    }

    @Test
    fun decodesTapDataObject() {
        assertEquals(ClientMessage.Tap, decodeClient("""{"type":"tap"}"""))
    }

    @Test
    fun decodesTextAndGlobal() {
        assertEquals(ClientMessage.Text("interstellar"), decodeClient("""{"type":"text","value":"interstellar"}"""))
        assertEquals(ClientMessage.Global("home"), decodeClient("""{"type":"global","action":"home"}"""))
    }

    @Test
    fun encodesWelcomeWithTypeDiscriminator() {
        val s = encodeServer(ServerMessage.Welcome(Screen(1920, 1080), 13, Features(imeEnter = true, scroll = true)))
        assertTrue(s.contains(""""type":"welcome""""))
        assertTrue(s.contains(""""android":13"""))
    }

    @Test
    fun ignoresUnknownKeys() {
        assertEquals(ClientMessage.Ping, decodeClient("""{"type":"ping","extra":1}"""))
    }
}
