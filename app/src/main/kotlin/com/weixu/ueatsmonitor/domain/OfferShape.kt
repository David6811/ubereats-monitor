package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Does this screen look like an offer card at all?
 *
 * The real card carries a payout and a total distance on the same screen
 * ("$86 / 27 min (6.0 km) total"). An earnings page carries money and no
 * distance, so this is what separates the two.
 */
object OfferShape {

    private val MONEY = Regex("""[$＄]\s*(\d+(?:[.,]\d{1,2})?)""")
    private val DISTANCE = Regex(
        """\d+(?:\.\d+)?\s*(?:km\b|kilomet(?:er|re)s?\b|mi\b|miles?\b|公里|英里)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Words that belong to a trip already accepted. Uber's own navigation screen
     * shows the day's earnings and the distance left to drive, which is the same
     * pair a card shows - and it shows them for the whole drive, so the chip sat
     * there saying "thinking" from the moment a job was taken until it was
     * delivered. A card being offered never says any of these.
     */
    private val ON_A_TRIP = listOf(
        "Deliver to",
        "Complete delivery",
        "Waybill",
        "Trip planner",
    )

    fun looksLikeOffer(text: String): Boolean =
        !onATrip(text) && paysSomething(text) && DISTANCE.containsMatchIn(text)

    private fun onATrip(text: String): Boolean =
        ON_A_TRIP.any { text.contains(it, ignoreCase = true) }

    /**
     * A payout of zero is not a payout. The navigation screen's earnings counter
     * reads $0.00 at the start of a shift, and that alone satisfied "there is
     * money on this screen".
     */
    private fun paysSomething(text: String): Boolean =
        MONEY.findAll(text).any { match ->
            (match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0
        }
}
