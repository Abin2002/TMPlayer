package com.tmplayer.desktop.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastPressesTest {

    @Test
    fun `a pressed toast's link runs its press once`() {
        var pressed = 0
        val link = ToastPresses.register { pressed++ }
        assertTrue(link.startsWith("tmplayer:press/"))
        // Windows may hand the link over with a trailing slash; other arguments are passed by.
        assertTrue(ToastPresses.handle(listOf("--minimized", "$link/")))
        assertEquals(1, pressed)
        assertFalse(ToastPresses.handle(listOf(link)))
        assertEquals(1, pressed)
    }

    @Test
    fun `an unknown or foreign link does nothing`() {
        assertFalse(ToastPresses.handle(emptyList()))
        assertFalse(ToastPresses.handle(listOf("tmplayer:press/999999")))
        assertFalse(ToastPresses.handle(listOf("tmplayer:press/abc", "tg://resolve?domain=x")))
    }

    @Test
    fun `old presses give their place up`() {
        var first = false
        val oldest = ToastPresses.register { first = true }
        repeat(70) { ToastPresses.register {} }
        assertFalse(ToastPresses.handle(listOf(oldest)))
        assertFalse(first)
    }
}
