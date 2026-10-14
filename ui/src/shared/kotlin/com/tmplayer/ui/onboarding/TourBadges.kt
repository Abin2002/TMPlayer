package com.tmplayer.ui.onboarding

import androidx.compose.ui.geometry.Offset
import com.tmplayer.data.DeviceForm

// Written by scripts/tour-badges.py from the tour specs in scripts/annotations/. Do not edit by hand.

/**
 * Where each numbered badge sits on a tour page's picture, as fractions of its width and height, in
 * the order of the page's lines. One list per page and form; a light and a dark picture share it.
 */
internal object TourBadges {
    fun of(page: TourPage, form: DeviceForm): List<Offset> = all[page to form].orEmpty()

    private val all: Map<Pair<TourPage, DeviceForm>, List<Offset>> = mapOf(

    )
}
