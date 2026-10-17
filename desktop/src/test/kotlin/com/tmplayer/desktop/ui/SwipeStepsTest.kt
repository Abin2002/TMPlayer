package com.tmplayer.desktop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** [SwipeSteps]: one page per sideways gesture, its momentum included. */
class SwipeStepsTest {

    private fun turns(steps: SwipeSteps, deltas: List<Pair<Float, Long>>): List<Int> =
        deltas.map { (delta, at) -> steps.feed(delta, at) }.filter { it != 0 }

    @Test
    fun `a long swipe with momentum turns one page`() {
        // A touchpad: small deltas every 16 ms for half a second, then the momentum tailing off.
        val swipe = (0 until 30).map { 0.2f to it * 16L } + (0 until 20).map { 0.05f to 480L + it * 30L }
        assertEquals(listOf(1), turns(SwipeSteps(), swipe))
    }

    @Test
    fun `two swipes with a pause between turn two pages`() {
        val steps = SwipeSteps()
        val first = (0 until 10).map { -0.2f to it * 16L }
        val second = (0 until 10).map { -0.2f to 1_000L + it * 16L }
        assertEquals(listOf(-1, -1), turns(steps, first + second))
    }

    @Test
    fun `one wheel notch turns a page, a spun wheel still one`() {
        assertEquals(listOf(1), turns(SwipeSteps(), listOf(1f to 0L)))
        assertEquals(listOf(1), turns(SwipeSteps(), (0 until 12).map { 1f to it * 40L }))
    }

    @Test
    fun `a wobble under the threshold turns nothing`() {
        assertEquals(emptyList<Int>(), turns(SwipeSteps(), listOf(0.2f to 0L, -0.2f to 10L, 0.2f to 20L)))
    }
}
