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
        (TourPage.Videos to DeviceForm.Phone) to listOf(Offset(0.067f, 0.785f), Offset(0.067f, 0.662f), Offset(0.669f, 0.047f)),
        (TourPage.Videos to DeviceForm.Tv) to listOf(Offset(0.05f, 0.613f), Offset(0.05f, 0.524f), Offset(0.266f, 0.19f)),
        (TourPage.Videos to DeviceForm.Desktop) to listOf(Offset(0.023f, 0.521f), Offset(0.239f, 0.148f), Offset(0.693f, 0.033f)),
        (TourPage.Home to DeviceForm.Phone) to listOf(Offset(0.039f, 0.119f), Offset(0.039f, 0.504f), Offset(0.02f, 0.022f)),
        (TourPage.Home to DeviceForm.Tv) to listOf(Offset(0.249f, 0.196f), Offset(0.249f, 0.639f), Offset(0.047f, 0.343f)),
        (TourPage.Home to DeviceForm.Desktop) to listOf(Offset(0.237f, 0.132f), Offset(0.237f, 0.459f), Offset(0.023f, 0.254f)),
        (TourPage.Player to DeviceForm.Phone) to listOf(Offset(0.468f, 0.822f), Offset(0.093f, 0.895f), Offset(0.633f, 0.036f)),
        (TourPage.Player to DeviceForm.Tv) to listOf(Offset(0.484f, 0.833f), Offset(0.044f, 0.906f), Offset(0.734f, 0.896f)),
        (TourPage.Player to DeviceForm.Desktop) to listOf(Offset(0.573f, 0.888f), Offset(0.023f, 0.933f), Offset(0.885f, 0.802f)),
        (TourPage.Controls to DeviceForm.Phone) to listOf(Offset(0.037f, 0.333f), Offset(0.824f, 0.183f), Offset(0.398f, 0.4f)),
        (TourPage.Controls to DeviceForm.Tv) to listOf(Offset(0.249f, 0.437f), Offset(0.042f, 0.171f), Offset(0.253f, 0.065f)),
        (TourPage.Controls to DeviceForm.Desktop) to listOf(Offset(0.411f, 0.461f), Offset(0.856f, 0.936f), Offset(0.651f, 0.167f)),
        (TourPage.Downloads to DeviceForm.Phone) to listOf(Offset(0.065f, 0.545f), Offset(0.378f, 0.131f), Offset(0.065f, 0.238f)),
        (TourPage.Downloads to DeviceForm.Tv) to listOf(Offset(0.078f, 0.72f), Offset(0.443f, 0.177f), Offset(0.078f, 0.331f)),
        (TourPage.Downloads to DeviceForm.Desktop) to listOf(Offset(0.307f, 0.389f), Offset(0.239f, 0.253f), Offset(0.237f, 0.73f)),
        (TourPage.TvHome to DeviceForm.Tv) to listOf(Offset(0.054f, 0.089f), Offset(0.065f, 0.256f)),
        (TourPage.Settings to DeviceForm.Phone) to listOf(Offset(0.078f, 0.229f), Offset(0.078f, 0.699f), Offset(0.078f, 0.803f), Offset(0.078f, 0.132f)),
        (TourPage.Settings to DeviceForm.Tv) to listOf(Offset(0.074f, 0.375f), Offset(0.074f, 0.786f), Offset(0.074f, 0.923f), Offset(0.074f, 0.238f)),
        (TourPage.Settings to DeviceForm.Desktop) to listOf(Offset(0.048f, 0.27f), Offset(0.048f, 0.611f), Offset(0.048f, 0.771f), Offset(0.048f, 0.02f)),
    )
}
