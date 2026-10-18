package com.tmplayer.data

/**
 * The notifications TMPlayer may post outside its own window (CP76), each with its own switch in
 * Settings, its own Android channel and its own rules for when it fires. All of them are rare,
 * none is posted on a TV (its launcher rows do that job), and every one but [Downloads] is off
 * until the viewer turns it on; on Android 13 and later turning one on is where the notification
 * permission is asked for, never at launch.
 *
 * The rules live beside each one: [DownloadResults], [EpisodeAlerts], [NewVideoAlerts],
 * [StorageAlerts], [PieceAlerts], [UpdateAlerts] and [SupportReminder].
 */
enum class Notice(val defaultOn: Boolean) {
    /** A download, a whole season or every piece of a cut video finished, or did not. */
    Downloads(defaultOn = true),

    /** A new episode of a show the viewer is watching, in any chat they can see. */
    NewEpisodes(defaultOn = false),

    /** Any new video in a starred chat: the older, wider alert. */
    NewVideos(defaultOn = false),

    /** The device running short of space, or the recently played videos over their limit. */
    LowStorage(defaultOn = false),

    /** A piece missing from a cut video the viewer played has been posted. */
    MissingPieces(defaultOn = false),

    /** A new version, said while the app is closed (the popup covers an open one). */
    UpdateReady(defaultOn = false),

    /** Now and then a star on GitHub, or a coffee. */
    Support(defaultOn = false),
}
