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
    /**
     * True when the button said Match rather than Accept: a Trip Radar offer,
     * broadcast to several drivers, where pressing it enters you for the trip
     * instead of assigning it to you. Judged by exactly the same rules - it only
     * changes what the driver is told, because taking it is not the same as
     * getting it.
     */
    val isMatch: Boolean,
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
    /**
     * The card's action button. Two words, not one: a plain offer says Accept on
     * a green button, while a Trip Radar offer - broadcast to several drivers at
     * once, where pressing it enters you rather than assigns you - says Match on
     * a black one. Both are offers, judged by the same rules.
     *
     * What must not match is a past tense from elsewhere in the app: "Accepted",
     * "Acceptance", "Matched".
     */
    private val ACCEPT = Regex("""(^|[^a-z])(accept|match)([^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val MATCH = Regex("""(^|[^a-z])match([^a-z]|$)""", RegexOption.IGNORE_CASE)
    private val PAYOUT = Regex("""^[$＄]\s*(\d+(?:[.,]\d{1,2})?)$""")
    /**
     * "18 min (8.6 km) total", and the long-haul form "1 hr 6 min (53.0 km) total".
     * Matched anywhere in the line: OCR prefixes it with clock-icon debris such as
     * ") " and reads the leading 1 of "1 hr" as a lowercase L.
     */
    private val TOTALS = Regex(
        """(?:(\d+)\s*h(?:r|rs|our|ours)?\.?\s*)?(\d+)\s*min\s*\(\s*([\d.]+)\s*(km|mi|miles?|kilomet(?:er|re)s?)\s*\)\s*total""",
        RegexOption.IGNORE_CASE,
    )

    /** Card furniture that is never an address. */
    private val CHROME = listOf(
        "accept", "decline", "delivery", "exclusive", "batched", "shop", "package",
        "est. earnings", "estimated earnings", "for completed trip", "incl.",
        "verify", "reserved", "scheduled",
    )

    /** Cheap presence test: the Accept button, plus a payout or a totals line. */
    fun looksLikeCard(lines: List<String>): Boolean {
        val clean = clean(lines)
        if (clean.none { ACCEPT.containsMatchIn(it) }) return false
        return clean.any { PAYOUT.containsMatchIn(it) } || clean.any { TOTALS.containsMatchIn(it) }
    }

    fun read(lines: List<String>): OfferCard? {
        val clean = clean(lines)
        val acceptAt = clean.indexOfLast { ACCEPT.containsMatchIn(it) }
        val isMatch = acceptAt >= 0 && MATCH.containsMatchIn(clean[acceptAt])
        val end = if (acceptAt >= 0) acceptAt else clean.size

        val totalsAt = clean.take(end).indexOfLast { TOTALS.containsMatchIn(it) }
        val payoutAt = clean.take(if (totalsAt >= 0) totalsAt else end)
            .indexOfLast { PAYOUT.containsMatchIn(it) }
        if (payoutAt < 0) return null
        val payout = PAYOUT.find(clean[payoutAt])?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.let(Cents::ofDollars) ?: return null

        // Without the Accept anchor, a totals line is the only thing that still
        // separates an offer from a screen that merely shows a price.
        if (acceptAt < 0 && totalsAt < 0) return null

        val totals = if (totalsAt >= 0) TOTALS.find(clean[totalsAt]) else null
        val minutes = totals?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val mins = match.groupValues[2].toIntOrNull() ?: return@let null
            Minutes(hours * 60 + mins)
        }
        val distance = totals?.let { match ->
            val amount = match.groupValues[3].toDoubleOrNull() ?: return@let null
            if (match.groupValues[4].startsWith("mi", ignoreCase = true)) Miles(amount) else amount.km2mi()
        }

        // Take the stops from just under the totals line - or under the payout when
        // OCR mangled it - never from the top of the screen, where the status bar
        // and every map label live.
        val stopsFrom = if (totalsAt >= 0) totalsAt + 1 else payoutAt + 1
        val stops = joinWrapped(clean.subList(stopsFrom.coerceIn(0, end), end))
            .filterNot(::isChrome)
            .filterNot(::looksLikeTotals)
            .filter { it.length >= MIN_STOP_LENGTH }
        if (stops.isEmpty()) return null

        return OfferCard(
            isMatch = isMatch,
            payout = payout,
            duration = minutes,
            distance = distance,
            pickup = stops.first(),
            // An address wraps over two or three lines and OCR keeps none of the
            // punctuation that would say where it ends, so everything below the
            // pickup is one destination.
            dropoff = stops.drop(1).joinToString(" ") { it.trimEnd(',') }.ifEmpty { stops.first() },
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
        lines.map { normalise(it.trim()) }.filter { it.isNotEmpty() }

    /**
     * Undo the letter/digit confusions OCR makes inside a totals line, and only
     * there: a lowercase L or capital I standing in for 1 before "hr" or "min",
     * and a capital O for 0 between digits.
     */
    private fun normalise(line: String): String = line
        .replace(Regex("""[lI](?=\s*hr)""", RegexOption.IGNORE_CASE), "1")
        .replace(Regex("""(?<=\d)[Oo](?=\d)"""), "0")
        .replace(Regex("""^[^\w$＄]+"""), "")

    /** Shorter than this and OCR is showing us a button edge, not an address. */
    private const val MIN_STOP_LENGTH = 4

    /** A line ending with a comma carries on into the next one. */
    private fun joinWrapped(lines: List<String>): List<String> {
        val joined = mutableListOf<String>()
        for (line in lines) {
            if (joined.isNotEmpty() && joined.last().endsWith(",")) {
                joined[joined.size - 1] = joined.last() + " " + line
            } else {
                joined += line
            }
        }
        return joined
    }

    /** A totals line OCR mangled past parsing is still not an address. */
    private fun looksLikeTotals(line: String): Boolean =
        line.contains("total", ignoreCase = true) || Regex("""min\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(line)

    private fun isChrome(line: String): Boolean =
        CHROME.any { line.contains(it, ignoreCase = true) }
}
