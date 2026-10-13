package com.tmplayer.desktop.os

/** Which side or corner of an undecorated window the pointer took hold of. */
enum class ResizeEdge(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    Left(true, false, false, false),
    Top(false, true, false, false),
    Right(false, false, true, false),
    Bottom(false, false, false, true),
    TopLeft(true, true, false, false),
    TopRight(false, true, true, false),
    BottomLeft(true, false, false, true),
    BottomRight(false, false, true, true),
}

/** The pure half of the resize edges: where a window goes when an edge is dragged. */
object WindowResize {

    /**
     * [start] dragged by ([dx], [dy]) at [edge], held at [minWidth] by [minHeight]. A left or top
     * edge that hits the minimum stops where it is instead of pushing the window across the
     * screen, as a native edge does.
     */
    fun resize(start: Bounds, edge: ResizeEdge, dx: Int, dy: Int, minWidth: Int, minHeight: Int): Bounds {
        var x = start.x
        var y = start.y
        var width = start.width
        var height = start.height
        if (edge.right) width = maxOf(minWidth, start.width + dx)
        if (edge.bottom) height = maxOf(minHeight, start.height + dy)
        if (edge.left) {
            width = maxOf(minWidth, start.width - dx)
            x = start.x + start.width - width
        }
        if (edge.top) {
            height = maxOf(minHeight, start.height - dy)
            y = start.y + start.height - height
        }
        return Bounds(x, y, width, height)
    }

    /**
     * Where a maximized window lands when its header is dragged: the floating size back, placed so
     * that the point under the pointer stays the same share of the way across the header.
     * [grabX] is the pointer's offset into the maximized window, [maximizedWidth] that window's
     * width, [pointer] the pointer in screen coordinates.
     */
    fun unmaximizeUnder(pointerX: Int, pointerY: Int, grabX: Int, grabY: Int, maximizedWidth: Int, floatingWidth: Int, floatingHeight: Int): Bounds {
        val share = if (maximizedWidth > 0) grabX.toDouble() / maximizedWidth else 0.5
        val x = pointerX - (floatingWidth * share).toInt()
        return Bounds(x, pointerY - grabY, floatingWidth, floatingHeight)
    }
}
