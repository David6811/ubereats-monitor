package com.weixu.ueatsmonitor.domain

/**
 * Data. The Uber offer card, read off the screen exactly as it is laid out:
 *
 *     Delivery  Exclusive
 *     $5
 *     Est. earnings for completed trip
 *     10 min (1.6 km) total
 *     Mario's Pizza And Pasta
 *     Cole Street & Nockolds Crescent, Noble Park
 *     Accept
 */
data class OfferCard(
    val payout: Cents,
    val duration: Minutes,
    val distance: Miles,
    val pickup: String,
    val dropoff: String,
)

/**
 * Calculation. Lines in, an [OfferCard] out, or null when this is not an offer.
 *
 * Line-based, not a regex over the whole blob: the card's meaning lives in its
 * order - the totals line anchors it, the payout is above, the two addresses
 * are below - and a blob search would happily pair a payout from the map with a
 * distance from somewhere else.
 */
object OfferCardReader {

    private val PAYOUT = Regex("""^[$＄]\s*(\d+(?:[.,]\d{1,2})?)$""")
    private val TOTALS = Regex(
        """^(\d+)\s*min\s*\(\s*([\d.]+)\s*(km|mi|miles?|kilomet(?:er|re)s?)\s*\)\s*total$""",
        RegexOption.IGNORE_CASE,
    )

    /** Card furniture that is never an address. */
    private val CHROME = listOf(
        "accept", "decline", "delivery", "exclusive", "batched", "est. earnings",
        "estimated earnings", "for completed trip", "verify", "включ",
    )

    fun read(lines: List<String>): OfferCard? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val totalsAt = clean.indexOfFirst { TOTALS.matches(it) }
        if (totalsAt < 0) return null

        val totals = TOTALS.find(clean[totalsAt]) ?: return null
        val minutes = totals.groupValues[1].toIntOrNull() ?: return null
        val amount = totals.groupValues[2].toDoubleOrNull() ?: return null
        val distance = if (totals.groupValues[3].startsWith("mi", ignoreCase = true)) {
            Miles(amount)
        } else {
            amount.km2mi()
        }

        val payout = clean.take(totalsAt)
            .asReversed()
            .firstNotNullOfOrNull { line -> PAYOUT.find(line)?.groupValues?.get(1) }
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let(Cents::ofDollars) ?: return null

        val addresses = clean.drop(totalsAt + 1).filter { line -> !isChrome(line) }
        if (addresses.isEmpty()) return null

        return OfferCard(
            payout = payout,
            duration = Minutes(minutes),
            distance = distance,
            pickup = addresses[0],
            dropoff = addresses.getOrElse(1) { addresses[0] },
        )
    }

    private fun isChrome(line: String): Boolean =
        CHROME.any { line.contains(it, ignoreCase = true) }

    /** The offer as the evaluator wants it. */
    fun toOffer(card: OfferCard): Offer = Offer(
        payout = card.payout,
        distance = card.distance,
        duration = card.duration,
        pickup = card.pickup,
        dropoff = card.dropoff,
    )
}
