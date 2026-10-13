package com.tmplayer.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

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

    @Test
    fun invitesOnlyAnOffLeftOverFromAnOlderVersionOnce() {
        val leftOver = MetaSettings(enabled = false)
        assertTrue(MetaNotice.shouldInvite(leftOver, inviteSeen = false))
        assertFalse(MetaNotice.shouldInvite(leftOver, inviteSeen = true))
        assertFalse(MetaNotice.shouldInvite(MetaSettings(enabled = false, offByChoice = true), inviteSeen = false))
        // A fresh install has lookups on, so there is nothing to invite to.
        assertFalse(MetaNotice.shouldInvite(MetaSettings(), inviteSeen = false))
    }

    @Test
    fun anOlderVersionsOffInvitesAndAChosenOffDoesNot() {
        val dir = createTempDirectory("meta-notice").toFile()
        try {
            val file = File(dir, "metadata.properties")
            // What an older version wrote: off, and nothing about why.
            file.writeText("enabled=false\nskip_times=true\n")
            assertTrue(MetaNotice.shouldInvite(MetadataStore(file).now, inviteSeen = false))
            MetadataStore(file).update { it.copy(offByChoice = true) }
            assertFalse(MetaNotice.shouldInvite(MetadataStore(file).now, inviteSeen = false))
        } finally {
            dir.deleteRecursively()
        }
    }
}
