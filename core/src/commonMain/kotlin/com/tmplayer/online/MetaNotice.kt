package com.tmplayer.online

/**
 * The grid's one-time note that posters and overviews are looked up online. Lookups are on by
 * default, so the first chat grid says so once, with Keep and Turn off, and never again after
 * either. With lookups already off there is nothing to say.
 */
object MetaNotice {
    fun shouldShow(enabled: Boolean, dismissed: Boolean): Boolean = enabled && !dismissed
}
