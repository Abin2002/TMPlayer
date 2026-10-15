package com.tmplayer.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Back between the pages the phone's floating bar reaches, and which bar button is lit. */
class TouchRoutesTest {

    private val side = setOf("settings", "downloads")

    private fun back(to: String, from: String, fromBack: String? = null, toBack: String? = null) =
        TouchRoutes.backTarget(to, from, fromBack, toBack, lateral = { it in side }, home = "browse")

    @Test
    fun `Settings from the browser goes back to the browser`() {
        assertEquals("browse", back(to = "settings", from = "browse"))
    }

    @Test
    fun `Downloads from a chat goes back to the chat, as Not enough space does`() {
        assertEquals("chat", back(to = "downloads", from = "chat"))
    }

    @Test
    fun `a step sideways keeps the Back of the page it was taken from`() {
        // A chat, Not enough space, Downloads, then Settings on the bar: Back from Settings is the chat.
        assertEquals("chat", back(to = "settings", from = "downloads", fromBack = "chat"))
        assertEquals("browse", back(to = "downloads", from = "settings", fromBack = "browse"))
    }

    @Test
    fun `Settings and Downloads never send Back to each other`() {
        // Downloads from Settings' Cached videos row, then Settings on the bar: Settings keeps its own Back.
        assertEquals("chat", back(to = "settings", from = "downloads", fromBack = "settings", toBack = "chat"))
        assertEquals("browse", back(to = "settings", from = "downloads", fromBack = "settings", toBack = null))
        assertEquals("browse", back(to = "settings", from = "downloads", fromBack = "settings", toBack = "downloads"))
    }

    @Test
    fun `the highlight follows the bar's order, Settings fourth`() {
        assertEquals(0, barHighlightIndex(TouchDestination.Home))
        assertEquals(1, barHighlightIndex(TouchDestination.Chats))
        assertEquals(2, barHighlightIndex(TouchDestination.Search))
        assertEquals(3, barHighlightIndex(TouchDestination.Settings))
        assertNull(barHighlightIndex(null))
    }

    @Test
    fun `a page with its own buttons keeps room for their pill over the bar`() {
        assert(touchShellClearance(withActions = true) > touchShellClearance(withActions = false))
        assertEquals(BAR_MARGIN + BAR_HEIGHT + BAR_GAP, touchShellClearance(withActions = false))
    }
}
