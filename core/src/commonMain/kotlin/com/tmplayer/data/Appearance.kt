package com.tmplayer.data

import com.tmplayer.i18n.L

/**
 * What the viewer chose in Settings, under Appearance.
 *
 * Stored by name rather than by ordinal, so reordering this list cannot silently move everybody's
 * setting to whichever entry now sits at their old number.
 *
 * @property label the one word shown in a segment, short enough that three fit across a phone
 * @property description the sentence under the picker, also read out by a screen reader
 */
enum class ThemeChoice(private val labelText: () -> String, private val descriptionText: () -> String) {
    /** Whatever the phone itself is set to, which is what almost everybody wants. */
    System({ L.themeSystem }, { L.themeSystemDetail }),
    Light({ L.themeLight }, { L.themeLightDetail }),
    Dark({ L.themeDark }, { L.themeDarkDetail }),
    ;

    /** The one word shown in a segment, in the UI language. */
    val label: String get() = labelText()

    /** The sentence under the picker, in the UI language. */
    val description: String get() = descriptionText()

    /**
     * The same sentence for a television, which has no system setting to match.
     *
     * Android TV has no light mode of its own, so "System" cannot mean "follow the device" there.
     * It means the dark panel, which is the right default for a screen watched in the evening.
     */
    val tvDescription: String
        get() = when (this) {
            System -> L.themeSystemTvDetail
            Light -> L.themeLightDetail
            Dark -> L.themeDarkDetail
        }

    companion object {
        val Default = System

        fun from(stored: String?): ThemeChoice =
            entries.firstOrNull { it.name == stored } ?: Default
    }
}

/**
 * How large the whole interface is drawn, under Appearance as "Interface size" (CP59).
 *
 * The [factor] multiplies the screen's density at the root of every composition, so a dp and an sp
 * both grow by it and every layout keeps its proportions. The system's own font scale is left as it
 * is and still applies on top: somebody with large text set on the device gets it at every size
 * here too. Stored by name, like [ThemeChoice].
 *
 * The video itself is never scaled: the picture fills the screen whatever is chosen, and only the
 * controls and panels over it follow.
 */
enum class UiScale(val factor: Float, private val labelText: () -> String) {
    Smaller(0.9f, { L.interfaceSizeSmaller }),
    Default(1f, { L.interfaceSizeDefault }),
    Larger(1.15f, { L.interfaceSizeLarger }),
    Largest(1.3f, { L.interfaceSizeLargest }),
    ;

    /** The word shown on the row and in the segments, in the UI language. */
    val label: String get() = labelText()

    /** The size [direction] steps away (-1 smaller, +1 larger), or this one at either end. */
    fun step(direction: Int): UiScale = entries.getOrNull(ordinal + direction) ?: this

    companion object {
        fun from(stored: String?): UiScale = entries.firstOrNull { it.name == stored } ?: Default
    }
}
