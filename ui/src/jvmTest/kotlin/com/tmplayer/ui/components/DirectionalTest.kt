package com.tmplayer.ui.components

import androidx.compose.ui.unit.LayoutDirection
import com.tmplayer.ui.icons.TmIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arrows and chevrons that point along the line turn round in a right-to-left interface. */
class DirectionalTest {

    @Test
    fun `a folded chevron points to the line's end`() {
        assertEquals(-90f, chevronTowardsEnd(LayoutDirection.Ltr))
        assertEquals(90f, chevronTowardsEnd(LayoutDirection.Rtl))
    }

    @Test
    fun `arrows along the line mirror themselves, ones across it do not`() {
        listOf(TmIcons.ArrowBack, TmIcons.ArrowForward, TmIcons.ChevronLeft, TmIcons.ChevronRight).forEach {
            assertTrue(it.name, it.autoMirror)
        }
        listOf(TmIcons.ChevronDown, TmIcons.ChevronUp, TmIcons.Play).forEach {
            assertFalse(it.name, it.autoMirror)
        }
    }
}
