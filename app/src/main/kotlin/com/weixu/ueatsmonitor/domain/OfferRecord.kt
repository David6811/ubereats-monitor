package com.weixu.ueatsmonitor.domain

/**
 * Data. What one saved capture says about the offer that was on screen.
 *
 * [ruling] and [why] are nullable because a card can be read a moment before the
 * rules have anything to say about it - a capture with a card and no ruling is
 * still an offer worth listing.
 */
data class OfferRecord(
    val isMatch: Boolean,
    val payout: String,
    val pickup: String,
    val dropoff: String,
    val ruling: String?,
    val why: String?,
)

/**
 * Calculation. Reads the header a capture file carries above its screen text.
 *
 * Null means no offer card was recognised on that frame, which is almost every
 * frame: the recorder saves the screen every couple of seconds all shift.
 */
object OfferRecordReader {

    fun read(raw: String): OfferRecord? {
        if (field(raw, "card") != "true") return null
        return OfferRecord(
            isMatch = field(raw, "card_kind") == "match",
            payout = field(raw, "card_payout") ?: return null,
            pickup = field(raw, "card_pickup") ?: return null,
            dropoff = field(raw, "card_dropoff") ?: return null,
            ruling = field(raw, "ruling"),
            why = field(raw, "ruling_why"),
        )
    }

    private fun field(raw: String, key: String): String? =
        Regex("^" + Regex.escape(key) + "=(.*)$", RegexOption.MULTILINE)
            .find(raw)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
