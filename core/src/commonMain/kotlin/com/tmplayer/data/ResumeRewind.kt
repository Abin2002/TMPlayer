package com.tmplayer.data

import com.tmplayer.i18n.L

/**
 * Starting a resumed video a few seconds before where it was left, the way a person picking up a
 * book rereads the last lines: the sentence that was cut off is heard whole, and the viewer finds
 * their place again before the story moves on.
 *
 * The same on every platform, set in Settings as "Rewind when resuming". Only a real resume is
 * moved: a video starting from the beginning stays at zero, and a saved position under
 * [MIN_SAVED_MS] is kept as it is, since stepping back from there is stepping back to the start.
 */
object ResumeRewind {
    const val DEFAULT_MS = 3_000L

    /** Zero is off. */
    val CHOICES_MS = listOf(0L, 3_000L, 5_000L, 10_000L)

    /** A saved position closer to the start than this is not moved. */
    const val MIN_SAVED_MS = 10_000L

    /** A stored value off the list falls back to the default, so a bad write cannot stick. */
    fun sanitise(ms: Long?): Long = ms?.takeIf { it in CHOICES_MS } ?: DEFAULT_MS

    /** Where a video saved at [savedMs] opens, with [rewindMs] taken off. Never below zero. */
    fun start(savedMs: Long, rewindMs: Long): Long {
        if (savedMs < MIN_SAVED_MS || rewindMs <= 0L) return savedMs.coerceAtLeast(0L)
        return (savedMs - rewindMs).coerceAtLeast(0L)
    }

    /** "Off", or "3 seconds". */
    fun label(ms: Long): String = if (ms <= 0L) L.commonOff else L.unitSeconds(ms / 1000)
}
