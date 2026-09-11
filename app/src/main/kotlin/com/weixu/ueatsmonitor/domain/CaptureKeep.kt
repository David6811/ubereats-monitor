package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Which saved frames to throw away.
 *
 * Almost every frame is a map or a waiting screen - on the day this was measured,
 * 61 of 20025 held an offer card. Those few are the shift's record and are kept
 * far longer than the rest, which are only worth having while something is being
 * diagnosed.
 */
object CaptureKeep {

    /**
     * [names] newest first. [offers] are the frames that held a card. Returns the
     * names to delete, so the caller can take their screenshots with them.
     */
    fun toDelete(
        names: List<String>,
        offers: Set<String>,
        keepFrames: Int,
        keepOffers: Int,
    ): Set<String> {
        var frames = 0
        var cards = 0
        val doomed = mutableSetOf<String>()
        for (name in names) {
            if (name in offers) {
                if (++cards > keepOffers) doomed += name
            } else {
                if (++frames > keepFrames) doomed += name
            }
        }
        return doomed
    }
}
