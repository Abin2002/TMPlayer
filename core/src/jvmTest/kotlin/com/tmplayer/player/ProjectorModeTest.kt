package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The projector resolution ceiling each choice means. */
class ProjectorModeTest {

    @Test
    fun `automatic has no ceiling`() {
        assertNull(ProjectorMode.maxSize(ProjectorMode.AUTO))
    }

    @Test
    fun `each cap is the display's own pixels`() {
        assertEquals(1920 to 1080, ProjectorMode.maxSize(ProjectorMode.P1080))
        assertEquals(1280 to 720, ProjectorMode.maxSize(ProjectorMode.P720))
        assertEquals(854 to 480, ProjectorMode.maxSize(ProjectorMode.P480))
    }

    @Test
    fun `an unknown choice is no ceiling rather than a crash`() {
        assertNull(ProjectorMode.maxSize(99))
    }
}
