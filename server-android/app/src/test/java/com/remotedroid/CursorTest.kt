package com.remotedroid

import com.remotedroid.input.Cursor
import org.junit.Assert.assertEquals
import org.junit.Test

class CursorTest {
    @Test
    fun movesScaledBySensitivity() {
        val c = Cursor(100f, 100f, 1920, 1080, sensitivity = 2f)
        c.move(10f, -5f)
        assertEquals(120f, c.x, 0.001f)
        assertEquals(90f, c.y, 0.001f)
    }

    @Test
    fun clampsToScreenBounds() {
        val c = Cursor(0f, 0f, 1920, 1080, sensitivity = 1f)
        c.move(-50f, -50f)
        assertEquals(0f, c.x, 0.001f)
        assertEquals(0f, c.y, 0.001f)
        c.move(99999f, 99999f)
        assertEquals(1919f, c.x, 0.001f)
        assertEquals(1079f, c.y, 0.001f)
    }
}
