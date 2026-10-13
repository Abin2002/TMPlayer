package com.tmplayer.online

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class MetaNoticeTest {
    @Test
    fun showsOnlyWhileLookupsAreOnAndTheNoteWasNotPutAway() {
        assertTrue(MetaNotice.shouldShow(enabled = true, dismissed = false))
        assertFalse(MetaNotice.shouldShow(enabled = true, dismissed = true))
        assertFalse(MetaNotice.shouldShow(enabled = false, dismissed = false))
        assertFalse(MetaNotice.shouldShow(enabled = false, dismissed = true))
    }

    @Test
    fun defaultsShowIt() {
        assertTrue(MetaNotice.shouldShow(MetaSettings().enabled, dismissed = false))
    }
}
