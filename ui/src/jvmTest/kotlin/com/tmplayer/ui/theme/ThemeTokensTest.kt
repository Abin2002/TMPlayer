package com.tmplayer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The figures the cinematic refresh promises about the palette and the scrims. */
class ThemeTokensTest {

    @Test
    fun `muted text stays readable on the ground and on a surface, in both themes`() {
        for (scheme in listOf(TmSchemes.Dark, TmSchemes.Light)) {
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.background) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f)
        }
        // The dark figures the palette's KDoc quotes.
        assertEquals(9.3f, contrast(TmSchemes.Dark.onSurfaceVariant, TmSchemes.Dark.background), 0.1f)
        assertEquals(8.6f, contrast(TmSchemes.Dark.onSurfaceVariant, TmSchemes.Dark.surfaceContainerLow), 0.1f)
    }

    @Test
    fun `the hero pill is 15 to 1 or better in both themes`() {
        // Tone.hero is onSurface, Tone.onHero the background.
        for (scheme in listOf(TmSchemes.Dark, TmSchemes.Light)) {
            assertTrue(contrast(scheme.onSurface, scheme.background) >= 15f)
        }
    }

    @Test
    fun `the accent still reads on the new ground`() {
        assertTrue(contrast(TmSchemes.Dark.primary, TmSchemes.Dark.background) >= 11f)
    }

    @Test
    fun `the long curve rises from clear to solid and is 0,84 by its last sixth`() {
        val curve = Scrims.LongCurve
        assertEquals(0f to 0f, curve.first())
        assertEquals(1f to 1f, curve.last())
        assertTrue(curve.zipWithNext().all { (a, b) -> b.first > a.first && b.second > a.second })
        assertEquals(0.84f, curve.first { it.first == 0.84f }.second)
    }

    @Test
    fun `the tile foot is clear above 45 per cent`() {
        val foot = Scrims.FootCurve
        assertTrue(foot.filter { it.first <= 0.45f }.all { it.second == 0f })
        assertEquals(0.95f, foot.last().second)
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
