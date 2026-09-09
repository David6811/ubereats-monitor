package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Does this screen look like an offer card at all?
 *
 * The real card carries a payout and a total distance on the same screen
 * ("$86 / 27 min (6.0 km) total"). An earnings page carries money and no
 * distance, so this is what separates the two.
 */
object OfferShape {

    private val MONEY = Regex("""[$＄]\s*\d""")
    private val DISTANCE = Regex(
        """\d+(?:\.\d+)?\s*(?:km\b|kilomet(?:er|re)s?\b|mi\b|miles?\b|公里|英里)""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeOffer(text: String): Boolean =
        MONEY.containsMatchIn(text) && DISTANCE.containsMatchIn(text)
}
