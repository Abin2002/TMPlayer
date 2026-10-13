package com.tmplayer.data

/**
 * The TV screensaver's settings and arithmetic (CP57). The screen itself is the Android app's;
 * what is here is what can be tested without one.
 *
 * Burn-in is the reason it exists at all: a browse screen left on a television for an evening
 * holds the same bright rail and the same tiles in the same pixels for hours. The screensaver is
 * mostly black, and what it does show (the clock, one picture) moves to a new spot every
 * [MOVE_EVERY_MS], so nothing stays lit in one place.
 */
object Screensaver {
    /** Off, then the minutes Settings offers. */
    val CHOICES = listOf(0, 2, 5, 10)
    const val DEFAULT_MINUTES = 5

    /** How long the clock and the picture stay in one spot. */
    const val MOVE_EVERY_MS = 60_000L

    /** How long one picture is shown before the next fades in over it. */
    const val PICTURE_EVERY_MS = 20_000L

    /** The next choice from [minutes] in [direction] (-1 or 1), held at the ends. */
    fun step(minutes: Int, direction: Int): Int {
        val at = CHOICES.indexOf(minutes).takeIf { it >= 0 } ?: CHOICES.indexOf(DEFAULT_MINUTES)
        return CHOICES[(at + direction).coerceIn(0, CHOICES.lastIndex)]
    }

    /** The idle time in milliseconds, or null when the screensaver is off. */
    fun idleMs(minutes: Int): Long? = if (minutes <= 0) null else minutes * 60_000L

    /**
     * Where the clock and picture sit for the [slot]th move, as fractions of the free room
     * (0 to 1 on each axis). A fixed walk round nine spots rather than a random one, so two moves
     * in a row never land on the same pixels, and the order skips about so it does not read as a
     * slow march across the screen.
     */
    fun spot(slot: Long): Pair<Float, Float> {
        val at = WALK[(slot % WALK.size).toInt().let { if (it < 0) it + WALK.size else it }]
        return (at % 3) / 2f to (at / 3) / 2f
    }

    private val WALK = intArrayOf(4, 0, 8, 2, 6, 1, 7, 3, 5)
}
