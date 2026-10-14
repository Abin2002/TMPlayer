package com.tmplayer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A display title loses its tight tracking only where joined letters or a right-to-left page would wrap it. */
class TrackingTest {

    private val display = TextStyle(fontSize = 32.sp, letterSpacing = (-0.5).sp)

    @Test
    fun `joined scripts are recognised`() {
        assertTrue(joinsLetters("لوكي"))
        assertTrue(joinsLetters("Loki لوكي"))
        assertTrue(joinsLetters("کاغذ")) // Persian
        assertFalse(joinsLetters("Loki"))
        assertFalse(joinsLetters("Локи"))
        assertFalse(joinsLetters("ロキ"))
        assertFalse(joinsLetters("לוקי")) // Hebrew is right to left but its letters stand apart
    }

    @Test
    fun `an Arabic title or a right-to-left page drops the negative tracking`() {
        assertEquals(0.sp, untracked(display, "لوكي", rtl = false).letterSpacing)
        assertEquals(0.sp, untracked(display, "Loki", rtl = true).letterSpacing)
        assertEquals((-0.5).sp, untracked(display, "Loki", rtl = false).letterSpacing)
    }

    @Test
    fun `positive or unset tracking is left alone`() {
        val wide = TextStyle(letterSpacing = 1.sp)
        assertEquals(wide, untracked(wide, "لوكي", rtl = true))
        val plain = TextStyle(fontSize = 32.sp)
        assertEquals(plain, untracked(plain, "لوكي", rtl = true))
    }
}
