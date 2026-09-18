package com.weixu.ueatsmonitor.domain

/**
 * Data. Whether an offer arriving right now would get a verdict on screen - the
 * one question the top of the app answers.
 *
 * Three separate lamps used to answer it, and all three had to be read to know.
 * The app could sit open with the reader stopped and nothing said it.
 */
sealed interface Watch {
    data object Watching : Watch

    /** [what] names the one thing to switch on, in the words the system page uses. */
    data class NotWatching(val what: Missing) : Watch
}

/** Data. What stands between an offer and a verdict, in the order to fix it. */
enum class Missing {
    /** The accessibility service is off: no card can be read at all. */
    READER,

    /** On, but no pass of its loop for a while: Android has stopped it. */
    READER_STALLED,

    /** The card is read and judged, but the verdict cannot be drawn. */
    OVERLAY,
}

/** Calculation. The state of the watch from what the system reports. */
object WatchJudge {

    /** Two passes of the loop without a frame: the reader has stopped, whatever it last said. */
    const val STALE_MILLIS = 6_000L

    fun judge(readerGranted: Boolean, sinceLastFrameMillis: Long, overlayGranted: Boolean): Watch = when {
        !readerGranted -> Watch.NotWatching(Missing.READER)
        sinceLastFrameMillis > STALE_MILLIS -> Watch.NotWatching(Missing.READER_STALLED)
        !overlayGranted -> Watch.NotWatching(Missing.OVERLAY)
        else -> Watch.Watching
    }
}
