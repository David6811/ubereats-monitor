package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Folds the frames of one offer into one record.
 *
 * A card sits on screen for the better part of a minute and the recorder saves
 * the screen every two seconds, so a single offer arrives as dozens of identical
 * frames. Two frames belong to the same offer when they name the same money and
 * the same two stops and nothing else came between them for long.
 */
object OfferRun {

    /** Longer than this between two identical cards and it is a second offer. */
    const val GAP_MILLIS = 3L * 60 * 1000

    /**
     * [items] newest first; the newest frame of each run is kept, in the same
     * order. [at] and [offer] read the two fields this needs off whatever the
     * caller is holding.
     */
    fun <T> collapse(items: List<T>, at: (T) -> Long, offer: (T) -> OfferRecord?): List<T> {
        val kept = mutableListOf<T>()
        var runKey: String? = null
        var runAt = 0L
        for (item in items) {
            val key = offer(item)?.let(::keyOf)
            val time = at(item)
            val continues = key != null && key == runKey && runAt - time <= GAP_MILLIS
            if (!continues) kept += item
            runKey = key
            runAt = time
        }
        return kept
    }

    /**
     * The pickup is deliberately not part of this. OCR reads the shop name with a
     * different scrap of the icon beside it from one frame to the next - "9 Guzman
     * y Gomez" then "p Guzman y Gomez" - which split one offer into two rows. Same
     * money to the same address within three minutes is the same offer.
     */
    private fun keyOf(offer: OfferRecord): String = listOf(
        if (offer.isMatch) "match" else "accept",
        offer.payout,
        offer.dropoff,
    ).joinToString("|")
}
