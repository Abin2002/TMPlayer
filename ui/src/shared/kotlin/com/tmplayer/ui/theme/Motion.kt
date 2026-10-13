package com.tmplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * How long things take to move, and how they ease, as one set of figures every screen reads.
 *
 * Before these existed each screen picked its own (`tween(140)` here, a spring default there), and
 * two things that should move together did not. Motion here moves pictures (a scale, a
 * cross-fade, a parallax) rather than repainting colours, and every piece of it asks
 * [rememberReducedMotion] first: with the system's animations off nothing scales, slides or
 * advances on its own. Cross-fades stay even then, because a fade is not motion.
 */
object Motion {

    /** A focus or a press settling: quick enough that a D-pad sweep never waits on it. */
    const val Fast = 150

    /** A picture arriving, a panel opening, a rail widening. */
    const val Normal = 220

    /** Something large changing place: a hero's backdrop, a page. */
    const val Slow = 400

    /** Material's standard curve: off the mark briskly and a long, soft arrival. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * How much a tile grows when the remote or the keyboard lands on it, on the television and
     * the desktop. More than the 1.02 a phone app would use, because the tiles here are smaller
     * in dp and the sofa is further away. [Tv.FocusClearance] is sized to leave room for it on a
     * 208 dp tile.
     */
    const val FocusScale = 1.05f

    /** How far a tile sinks under a finger. */
    const val PressScale = 0.97f

    /** How long a hero holds one item before it moves on to the next. */
    const val HeroAdvanceMs = 10_000L

    /** The first advance waits longer, so a viewer who has just arrived can read the first item. */
    const val HeroFirstAdvanceMs = 20_000L

    /** A tween of [durationMillis] on [Standard]. */
    fun <T> standard(durationMillis: Int = Normal): TweenSpec<T> = tween(durationMillis, easing = Standard)
}
