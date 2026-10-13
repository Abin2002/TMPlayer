package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** The rewind on resume choice is a setting, not a resume point, and the history sweeps leave it be. */
class ResumeRewindSettingTest {

    private fun store() = SettingsStore(
        SettingsStore.openDataStore(Files.createTempDirectory("tm-rewind").resolve(SettingsStore.FILE_NAME).toFile()),
    )

    @Test
    fun `the launch prune does not take the rewind choice for a broken resume point`() = runBlocking {
        val settings = store()
        settings.setResumeRewindMs(10_000L)
        assertEquals(0, settings.pruneBrokenHistory())
        assertEquals(10_000L, settings.resumeRewindMs.first())
    }

    @Test
    fun `clearing the watch history keeps the rewind choice`() = runBlocking {
        val settings = store()
        settings.setResumeRewindMs(0L)
        settings.clearWatchHistory()
        assertEquals(0L, settings.resumeRewindNow())
        assertEquals(emptyMap<String, Any>(), settings.watchProgress.first())
    }
}
