package com.tmplayer.player

/**
 * Projector display mode: the ceiling Settings puts on the video resolution, for a projector or
 * a weak stick driving one that a 4K stream would only make stutter.
 *
 * Pure mapping, so Settings, the player and its tests agree on what each choice means without
 * any of them knowing the others' numbers.
 */
object ProjectorMode {
    /** No ceiling: the player takes whatever the file offers. */
    const val AUTO = 0

    /** Cap the picture at 1920x1080. */
    const val P1080 = 1

    /** Cap the picture at 1280x720. */
    const val P720 = 2

    /** Cap the picture at 854x480, for the oldest portable projectors. */
    const val P480 = 3

    /** The largest picture the player may ask for at [mode], width then height, or null for none. */
    fun maxSize(mode: Int): Pair<Int, Int>? = when (mode) {
        P1080 -> 1920 to 1080
        P720 -> 1280 to 720
        P480 -> 854 to 480
        else -> null
    }
}
