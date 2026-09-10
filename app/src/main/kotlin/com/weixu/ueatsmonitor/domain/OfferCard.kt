package com.weixu.ueatsmonitor.domain

/**
 * Data. The Uber offer card, read off the screen as it is laid out:
 *
 *     Delivery  Exclusive
 *     $5
 *     Est. earnings for completed trip
 *     10 min (1.6 km) total
 *     Mario's Pizza And Pasta
 *     Cole Street & Nockolds Crescent, Noble Park
 *     Accept
 *
 * [duration] and [distance] are nullable on purpose: a garbled totals line must
 * still leave a card that says an offer appeared, rather than no card at all.
 */
data class OfferCard(
    val payout: Cents,
    val duration: Minutes?,
    val distance: Miles?,
    val pickup: String,
    val dropoff: String,
    val stops: List<String>,
)

/**
 * Calculation. Lines in, an [OfferCard] out, or null when this is not an offer.
 *
 * Anchored on the Accept button, which appears on the offer card and nowhere
 * else in the driver app - not on the map, not on the earnings page. Everything
 * else is found by walking up from it: the addresses sit directly above, the
 * totals line above those, the payout above that.
 */
object OfferCardReader {

    /**
     * The Accept button, however Android happens to describe it: bare, with a
     * countdown ("Accept 12s"), or with a role suffix ("Accept, button"). What it
     * must not match is a past tense elsewhere in the app - "Accepted", "Acceptance".
     */
    private val ACCEPT = Regex("""(^|[^a-z])accept([^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val PAYOUT = Regex("""^[$＄]\s*(\d+(?:[.,]\d{1,2})?)$""")
    private val TOTALS = Regex(
        """^(\d+)\s*min\s*\(\s*([\d.]+)\s*(km|mi|miles?|kilomet(?:er|re)s?)\s*\)\s*total$""",
        RegexOption.IGNORE_CASE,
    )

    /** Card furniture that is never an address. */
    private val CHROME = listOf(
        "accept", "decline", "delivery", "exclusive", "batched", "shop",
        "est. earnings", "estimated earnings", "for completed trip", "incl.",
        "verify", "reserved", "scheduled",
    )

    /** Cheap presence test: the Accept button, plus a payout or a totals line. */
    fun looksLikeCard(lines: List<String>): Boolean {
        val clean = clean(lines)
        if (clean.none { ACCEPT.containsMatchIn(it) }) return false
        return clean.any { PAYOUT.matches(it) } || clean.any { TOTALS.matches(it) }
    }

    fun read(lines: List<String>): OfferCard? {
        val clean = clean(lines)
        val acceptAt = clean.indexOfLast { ACCEPT.containsMatchIn(it) }
        val end = if (acceptAt >= 0) acceptAt else clean.size

        val totalsAt = clean.take(end).indexOfLast { TOTALS.matches(it) }
        val payout = clean.take(if (totalsAt >= 0) totalsAt else end)
            .asReversed()
            .firstNotNullOfOrNull { line -> PAYOUT.find(line)?.groupValues?.get(1) }
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let(Cents::ofDollars) ?: return null

        // Without the Accept anchor, a totals line is the only thing that still
        // separates an offer from a screen that merely shows a price.
        if (acceptAt < 0 && totalsAt < 0) return null

        val totals = if (totalsAt >= 0) TOTALS.find(clean[totalsAt]) else null
        val minutes = totals?.groupValues?.get(1)?.toIntOrNull()?.let(::Minutes)
        val distance = totals?.let { match ->
            val amount = match.groupValues[2].toDoubleOrNull() ?: return@let null
            if (match.groupValues[3].startsWith("mi", ignoreCase = true)) Miles(amount) else amount.km2mi()
        }

        val stops = clean.subList((totalsAt + 1).coerceAtLeast(0), end).filterNot(::isChrome)
        if (stops.isEmpty()) return null

        return OfferCard(
            payout = payout,
            duration = minutes,
            distance = distance,
            pickup = stops.first(),
            dropoff = stops.last(),
            stops = stops,
        )
    }

    /** The offer as the evaluator wants it. */
    fun toOffer(card: OfferCard): Offer = Offer(
        payout = card.payout,
        distance = card.distance,
        duration = card.duration,
        pickup = card.pickup,
        dropoff = card.dropoff,
    )

    private fun clean(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() }

    private fun isChrome(line: String): Boolean =
        CHROME.any { line.contains(it, ignoreCase = true) }
}
