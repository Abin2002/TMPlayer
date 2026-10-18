package com.tmplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The name of an icon-only control, on a hover: [TipBubble] over it (under it with
 * [TipSide.Below], for a control near the top of the window), with the key that does the same
 * beside the name where there is one.
 *
 * [modifier] is the control's place in its parent: the tip's box is what the parent lays out.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconTip(
    label: String,
    modifier: Modifier = Modifier,
    shortcut: String? = null,
    side: TipSide = TipSide.Above,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = { TipBubble(label, shortcut) },
        modifier = modifier,
        delayMillis = 500,
        tooltipPlacement = when (side) {
            TipSide.Below -> TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, alignment = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
            TipSide.End -> TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(8.dp, 0.dp))
            TipSide.Above -> TooltipPlacement.ComponentRect(anchor = Alignment.TopCenter, alignment = Alignment.TopCenter, offset = DpOffset(0.dp, (-8).dp))
        },
        content = content,
    )
}
