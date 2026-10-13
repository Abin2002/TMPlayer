package com.tmplayer.desktop.os

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowResizeTest {

    private val start = Bounds(100, 100, 1200, 800)

    @Test
    fun `right and bottom edges grow the window in place`() {
        assertEquals(Bounds(100, 100, 1250, 800), WindowResize.resize(start, ResizeEdge.Right, 50, 30, 960, 600))
        assertEquals(Bounds(100, 100, 1200, 830), WindowResize.resize(start, ResizeEdge.Bottom, 50, 30, 960, 600))
        assertEquals(Bounds(100, 100, 1250, 830), WindowResize.resize(start, ResizeEdge.BottomRight, 50, 30, 960, 600))
    }

    @Test
    fun `left and top edges move the window and keep the far side`() {
        assertEquals(Bounds(60, 100, 1240, 800), WindowResize.resize(start, ResizeEdge.Left, -40, 0, 960, 600))
        assertEquals(Bounds(100, 140, 1200, 760), WindowResize.resize(start, ResizeEdge.Top, 0, 40, 960, 600))
        assertEquals(Bounds(140, 140, 1160, 760), WindowResize.resize(start, ResizeEdge.TopLeft, 40, 40, 960, 600))
    }

    @Test
    fun `the minimum size holds and a left edge stops instead of pushing`() {
        assertEquals(Bounds(100, 100, 960, 800), WindowResize.resize(start, ResizeEdge.Right, -900, 0, 960, 600))
        // The right side stays at 1300 whatever the pointer does.
        assertEquals(Bounds(340, 100, 960, 800), WindowResize.resize(start, ResizeEdge.Left, 900, 0, 960, 600))
        assertEquals(Bounds(100, 300, 1200, 600), WindowResize.resize(start, ResizeEdge.Top, 0, 900, 960, 600))
    }

    @Test
    fun `a maximized window comes back under the pointer`() {
        // Grabbed at the middle of a 1920 wide header: the 1200 wide window is centred on the pointer.
        assertEquals(Bounds(400, 290, 1200, 800), WindowResize.unmaximizeUnder(1000, 300, 960, 10, 1920, 1200, 800))
    }
}
