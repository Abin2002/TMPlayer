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
        (TourPage.Videos to DeviceForm.Phone) to listOf(Offset(0.065f, 0.14f), Offset(0.065f, 0.5f), Offset(0.606f, 0.922f)),
        (TourPage.Videos to DeviceForm.Tv) to listOf(Offset(0.292f, 0.428f), Offset(0.047f, 0.519f), Offset(0.266f, 0.19f)),
        (TourPage.Videos to DeviceForm.Desktop) to listOf(Offset(0.016f, 0.185f), Offset(0.016f, 0.273f), Offset(0.714f, 0.023f)),
        (TourPage.Home to DeviceForm.Phone) to listOf(Offset(0.037f, 0.626f), Offset(0.104f, 0.435f), Offset(0.282f, 0.922f)),
        (TourPage.Home to DeviceForm.Tv) to listOf(Offset(0.249f, 0.617f), Offset(0.252f, 0.265f), Offset(0.047f, 0.43f)),
        (TourPage.Home to DeviceForm.Desktop) to listOf(Offset(0.016f, 0.655f), Offset(0.016f, 0.407f), Offset(0.426f, 0.91f)),
        (TourPage.Player to DeviceForm.Phone) to listOf(Offset(0.468f, 0.822f), Offset(0.093f, 0.895f), Offset(0.633f, 0.036f)),
        (TourPage.Player to DeviceForm.Tv) to listOf(Offset(0.471f, 0.839f), Offset(0.044f, 0.915f), Offset(0.734f, 0.909f)),
        (TourPage.Player to DeviceForm.Desktop) to listOf(Offset(0.562f, 0.887f), Offset(0.023f, 0.932f), Offset(0.885f, 0.802f)),
        (TourPage.Controls to DeviceForm.Phone) to listOf(Offset(0.037f, 0.333f), Offset(0.824f, 0.183f), Offset(0.398f, 0.4f)),
        (TourPage.Controls to DeviceForm.Tv) to listOf(Offset(0.25f, 0.506f), Offset(0.044f, 0.178f), Offset(0.252f, 0.063f)),
        (TourPage.Controls to DeviceForm.Desktop) to listOf(Offset(0.411f, 0.461f), Offset(0.856f, 0.936f), Offset(0.651f, 0.167f)),
        (TourPage.Downloads to DeviceForm.Phone) to listOf(Offset(0.074f, 0.701f), Offset(0.074f, 0.478f), Offset(0.074f, 0.174f)),
        (TourPage.Downloads to DeviceForm.Tv) to listOf(Offset(0.146f, 0.844f), Offset(0.231f, 0.198f), Offset(0.062f, 0.341f)),
        (TourPage.Downloads to DeviceForm.Desktop) to listOf(Offset(0.312f, 0.213f), Offset(0.316f, 0.143f), Offset(0.021f, 0.148f)),
        (TourPage.TvHome to DeviceForm.Tv) to listOf(Offset(0.054f, 0.089f), Offset(0.065f, 0.256f)),
        (TourPage.Settings to DeviceForm.Phone) to listOf(Offset(0.078f, 0.317f), Offset(0.078f, 0.738f), Offset(0.078f, 0.83f), Offset(0.078f, 0.232f)),
        (TourPage.Settings to DeviceForm.Tv) to listOf(Offset(0.074f, 0.396f), Offset(0.074f, 0.819f), Offset(0.074f, 0.925f), Offset(0.074f, 0.29f)),
        (TourPage.Settings to DeviceForm.Desktop) to listOf(Offset(0.03f, 0.331f), Offset(0.03f, 0.672f), Offset(0.03f, 0.754f), Offset(0.03f, 0.237f)),
    )
}
