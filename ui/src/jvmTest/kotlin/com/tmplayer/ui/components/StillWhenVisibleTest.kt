package com.tmplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalFoundationApi::class)
class StillWhenVisibleTest {

    /** A television's rule: every focused item to three tenths of the way down. */
    private val pivot = object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
            offset - containerSize * 0.3f
    }

    private val spec = stillWhenVisible(pivot)

    @Test
    fun `an item already in full view does not move the list`() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 600f, size = 300f, containerSize = 1000f), 0f)
        assertEquals(0f, spec.calculateScrollDistance(offset = 0f, size = 1000f, containerSize = 1000f), 0f)
    }

    @Test
    fun `an item cut by either edge scrolls as the platform would`() {
        assertEquals(pivot.calculateScrollDistance(-40f, 300f, 1000f), spec.calculateScrollDistance(-40f, 300f, 1000f), 0f)
        assertEquals(pivot.calculateScrollDistance(800f, 300f, 1000f), spec.calculateScrollDistance(800f, 300f, 1000f), 0f)
    }
}
