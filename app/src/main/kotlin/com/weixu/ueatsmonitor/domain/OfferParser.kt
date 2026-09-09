package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Notification text in, [ParseResult] out. No clock, no IO, no Android.
 * Uber's wording changes between app versions and locales, so this reads loosely:
 * a payout is required, everything else is best effort and stays null when absent.
 */
object OfferParser {

    /** The package that posts driver offers. Kept here so tests can name it. */
    const val UBER_DRIVER_PACKAGE: String = "com.ubercab.driver"

    private val MONEY = Regex("""[$＄]\s*(\d+(?:[.,]\d{1,2})?)""")
    private val MILES = Regex("""(\d+(?:\.\d+)?)\s*(?:mi\b|miles?\b)""", RegexOption.IGNORE_CASE)
    private val KILOMETERS = Regex("""(\d+(?:\.\d+)?)\s*(?:km\b|kilomet(?:er|re)s?\b)""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s*(?:mins?\b|minutes?\b)""", RegexOption.IGNORE_CASE)
    private val PICKUP = Regex("""\b(?:from|pickup:?)\s+([^·|,\n]{2,40})""", RegexOption.IGNORE_CASE)
    private val DROPOFF = Regex("""\b(?:to|drop\s?off:?|deliver to)\s+([^·|,\n]{2,40})""", RegexOption.IGNORE_CASE)

    /** Words that mean "this was an offer" even when the payout failed to parse. */
    private val OFFER_WORDS = listOf(
        "delivery request", "new order", "new delivery", "offer",
        "pickup", "deliver", "order request",
    )

    fun parse(raw: RawNotification): ParseResult {
        val body = raw.body
        if (body.isBlank()) return ParseResult.NotAnOffer

        val payout = MONEY.find(body)?.let { match ->
            match.groupValues[1].replace(',', '.').toDoubleOrNull()
        }?.let(Cents::ofDollars)

        if (payout == null) {
            val looksLikeOffer = OFFER_WORDS.any { body.contains(it, ignoreCase = true) }
            return if (looksLikeOffer) {
                ParseResult.Unreadable("found offer wording but no payout amount")
            } else {
                ParseResult.NotAnOffer
            }
        }

        return ParseResult.Parsed(
            Offer(
                payout = payout,
                distance = parseDistance(body),
                duration = MINUTES.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let(::Minutes),
                pickup = PICKUP.find(body)?.groupValues?.get(1)?.trim(),
                dropoff = DROPOFF.find(body)?.groupValues?.get(1)?.trim(),
            )
        )
    }

    private fun parseDistance(body: String): Miles? {
        MILES.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return Miles(it) }
        KILOMETERS.find(body)?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it.km2mi() }
        return null
    }
}
