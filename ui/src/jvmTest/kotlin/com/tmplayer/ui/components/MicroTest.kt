package com.tmplayer.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a popover of choices lands beside its button, and how far a pressed button sinks. */
class MicroTest {

    private val room = 1080 to 2400

    @Test
    fun `a popover sits above its button, centred on it`() {
        val button = Rect(500f, 2200f, 600f, 2300f)
        val spot = popoverSpot(button, card = 400 to 600, room = room, margin = 36, gap = 24)
        assertEquals(IntOffset(350, 2200 - 24 - 600), spot.offset)
        assertEquals(0.5f, spot.origin.pivotFractionX, 0.001f)
        assertEquals(1f, spot.origin.pivotFractionY, 0.001f)
    }

    @Test
    fun `a popover near the edge is held clear of it and grows from the button's side`() {
        val button = Rect(980f, 2200f, 1060f, 2300f)
        val spot = popoverSpot(button, card = 400 to 600, room = room, margin = 36, gap = 24)
        assertEquals(1080 - 400 - 36, spot.offset.x)
        assertTrue(spot.origin.pivotFractionX > 0.5f)
    }

    @Test
    fun `with no room above, a popover opens below its button`() {
        val button = Rect(500f, 100f, 600f, 200f)
        val spot = popoverSpot(button, card = 400 to 600, room = room, margin = 36, gap = 24)
        assertEquals(224, spot.offset.y)
        assertEquals(0f, spot.origin.pivotFractionY, 0.001f)
    }

    @Test
    fun `with no button known, a popover sits at the foot in the middle`() {
        val spot = popoverSpot(null, card = 400 to 600, room = room, margin = 36, gap = 24)
        assertEquals(IntOffset(340, 2400 - 600 - 36), spot.offset)
    }

    @Test
    fun `a press sinks a button, unless motion is reduced`() {
        assertEquals(Micro.PressScale, Micro.pressTarget(pressed = true, reduced = false), 0f)
        assertEquals(1f, Micro.pressTarget(pressed = true, reduced = true), 0f)
        assertEquals(1f, Micro.pressTarget(pressed = false, reduced = false), 0f)
    }
}
