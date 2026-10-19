package com.tmplayer.player

import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import com.tmplayer.R

/**
 * Stacks the loading and failure sheet, or lays it out in two columns where stacking will not fit.
 *
 * A phone held sideways has around 390dp of height, and the sheet is a poster, a title, a message
 * and up to three buttons: stacked, that overflows and the buttons fall off the bottom of the
 * display. Side by side it is a picture on the left and everything readable on the right, with the
 * buttons in a row under the words. A television has the height and keeps the stack.
 *
 * Applied every time the sheet is put up rather than once at startup: the player chooses its own
 * orientation and is not recreated when it changes, so the screen it was built against is regularly
 * not the screen it ends up on. Kept out of the activity so the render tests draw the sheet in the
 * shape the player gives it.
 */
object StatusSheetShape {

    /** [overlay] is `status_overlay` from activity_player.xml; [sideBySide] is a handset. */
    fun apply(overlay: View, sideBySide: Boolean) {
        val sheet = overlay.findViewById<LinearLayout>(R.id.status_sheet) ?: return
        val column = overlay.findViewById<LinearLayout>(R.id.status_column) ?: return
        val side = overlay.findViewById<LinearLayout>(R.id.status_side) ?: return
        val wanted = if (sideBySide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (sheet.orientation == wanted) return
        sheet.orientation = wanted

        val resources = overlay.resources
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()
        val poster = overlay.findViewById<ImageView>(R.id.status_poster)
        val name = overlay.findViewById<TextView>(R.id.status_name)
        val text = overlay.findViewById<TextView>(R.id.status_text)
        val detail = overlay.findViewById<TextView>(R.id.status_detail)

        // The picture side takes a narrow, fixed slice: it is there to be recognised, not read, so
        // every point it does not use goes to the message and the buttons.
        side.updateLayoutParams<LinearLayout.LayoutParams> {
            marginEnd = if (sideBySide) px(POSTER_GAP_DP) else 0
        }
        poster?.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (sideBySide) px(SIDE_POSTER_WIDTH_DP) else resources.getDimensionPixelSize(R.dimen.player_poster_width)
            height = if (sideBySide) px(SIDE_POSTER_HEIGHT_DP) else resources.getDimensionPixelSize(R.dimen.player_poster_height)
            // The gap between the two moves with them: what was space underneath the poster is now
            // space to the right of the pair.
            bottomMargin = if (sideBySide) px(NAME_GAP_DP) else px(POSTER_STACK_GAP_DP)
        }
        // The name wraps to the width of the picture it belongs to rather than running the width of
        // the screen, which is what keeps the two of them reading as one thing on the left.
        name?.apply {
            maxWidth = if (sideBySide) px(SIDE_POSTER_WIDTH_DP) else px(NAME_STACK_MAX_WIDTH_DP)
            maxLines = if (sideBySide) SIDE_NAME_LINES else STACK_NAME_LINES
        }
        // Weighted rather than wrapped, so a long message takes the width that is left over
        // instead of pushing the picture off the side of the screen.
        column.updateLayoutParams<LinearLayout.LayoutParams> {
            width = if (sideBySide) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
            weight = if (sideBySide) 1f else 0f
        }
        // The bars run the width of the column they are in rather than a width chosen for a screen
        // with nothing beside them.
        listOfNotNull<View>(overlay.findViewById(R.id.status_progress), overlay.findViewById(R.id.status_spinner)).forEach { bar ->
            bar.updateLayoutParams<LinearLayout.LayoutParams> {
                width = if (sideBySide) {
                    LinearLayout.LayoutParams.MATCH_PARENT
                } else {
                    resources.getDimensionPixelSize(R.dimen.player_progress_width)
                }
            }
        }
        // Both columns read from the same left edge, and the words run into the width they have
        // been given. Centred is right for a stacked sheet, where the column is the whole screen,
        // and wrong beside a picture.
        val flow = if (sideBySide) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        sheet.gravity = flow
        column.gravity = flow
        side.gravity = flow
        listOfNotNull<TextView>(name, overlay.findViewById(R.id.status_title), overlay.findViewById(R.id.status_meta), text, detail).forEach {
            it.gravity = if (sideBySide) Gravity.START else Gravity.CENTER
        }
        // Beside a picture the buttons go in a row, each as wide as its words, so all of them are
        // on the screen at once instead of a column of wide pills running off the bottom.
        val actions = overlay.findViewById<SheetActions>(R.id.status_actions)
        actions?.inRow = sideBySide
        if (actions != null) {
            for (i in 0 until actions.childCount) {
                val button = actions.getChildAt(i) as? Button ?: continue
                button.minWidth = if (sideBySide) px(ROW_BUTTON_MIN_WIDTH_DP) else px(STACK_BUTTON_MIN_WIDTH_DP)
                button.minimumWidth = button.minWidth
            }
        }
        // The message and its hint read as one paragraph beside the poster, not two blocks.
        text?.updateLayoutParams<LinearLayout.LayoutParams> {
            bottomMargin = if (sideBySide) px(ROW_TEXT_GAP_DP) else px(STACK_TEXT_GAP_DP)
        }
        detail?.updateLayoutParams<LinearLayout.LayoutParams> {
            topMargin = if (sideBySide) px(ROW_TEXT_GAP_DP) else px(STACK_DETAIL_GAP_DP)
        }
    }

    /** The gap between the poster and the words, beside them and above them. */
    private const val POSTER_GAP_DP = 24f
    private const val POSTER_STACK_GAP_DP = 22f

    /** The picture's slice of a sideways sheet, and the name that sits under it. */
    private const val SIDE_POSTER_WIDTH_DP = 176f
    private const val SIDE_POSTER_HEIGHT_DP = 99f
    private const val NAME_GAP_DP = 10f
    private const val SIDE_NAME_LINES = 3
    private const val STACK_NAME_LINES = 2
    private const val NAME_STACK_MAX_WIDTH_DP = 720f

    /** The sheet's buttons: a wide pill in a column, as wide as its words in a row. */
    private const val STACK_BUTTON_MIN_WIDTH_DP = 200f
    private const val ROW_BUTTON_MIN_WIDTH_DP = 112f

    /** Between the message, its hint and the buttons, stacked and beside a picture. */
    private const val STACK_TEXT_GAP_DP = 20f
    private const val STACK_DETAIL_GAP_DP = 14f
    private const val ROW_TEXT_GAP_DP = 8f
}
