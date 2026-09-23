package com.weixu.ueatsmonitor.domain

/**
 * Calculation. The two lines under the verdict on the offer card.
 *
 * Only the suburbs, because a street name is unreadable in the second the card
 * gives you, and only the rate, because the payout on its own says nothing until
 * it is divided by the distance.
 */
object ChipText {

    /** Data. The route line, and which stretch of it names a shop that is hard to park at. */
    data class Route(val text: String, val warnFrom: Int, val warnTo: Int) {
        val hasWarning: Boolean get() = warnTo > warnFrom
    }

    /** "Springvale（快餐·商场） → Mulgrave". Never empty: an unknown end names itself. */
    fun route(card: OfferCard, suburbs: List<Suburb>, stores: List<Store>, lang: Lang): Route {
        val from = suburbOf(card.pickup, suburbs) ?: shorten(card.pickup)
        val to = suburbOf(card.dropoff, suburbs) ?: shorten(card.dropoff)
        val shop = StoreKinds.find(card.pickup, stores)
        // English brackets need the space in front that full-width ones carry.
        val english = lang == Lang.ENGLISH
        val kind = shop?.let { found ->
            listOfNotNull(StoreKinds.label(found.kind, lang), StoreKinds.where(found.setting, lang))
                .joinToString(
                    separator = if (english) ", " else "·",
                    prefix = if (english) " (" else "（",
                    postfix = if (english) ")" else "）",
                )
        } ?: ""
        return Route(
            text = from + kind + " → " + to,
            // A shopping strip or a mall means no parking at the door, which is the
            // one thing on this line worth shouting.
            warnFrom = if (kind.isNotEmpty() && StoreKinds.hardToPark(shop!!.setting)) from.length else 0,
            warnTo = if (kind.isNotEmpty() && StoreKinds.hardToPark(shop!!.setting)) from.length + kind.length else 0,
        )
    }

    /**
     * What to call a stop whose suburb we do not know: the shop's own name, as
     * short as it can be said. OCR leaves a scrap of the icon in front, the
     * suburb sits in brackets behind, and an address carries on past its first
     * comma - none of that survives.
     */
    /**
     * How far the drop is from where this set is worked from, and which way.
     *
     * The word in front says how well the place is known, because the three
     * answers are not the same answer: a junction is a corner, a road is a
     * street, a suburb is a guess. Saying "6.2 km" for all three would be a lie
     * for two of them.
     */
    fun fromCentre(spot: Spot, centre: GeoPoint?, lang: Lang): String? {
        if (centre == null) return null
        val words = wordsIn(lang)
        val heading = Geo.headingTo(centre, spot.at)
        val km = heading.straightLine.value * MILES_TO_KM
        val way = heading.compass.labelIn(lang)
        val near = "%.1f".format(km)
        return when (spot) {
            is Spot.AtCrossing -> words.fromCentreExact(near, way)
            is Spot.NearCrossing -> words.fromCentreAbout(near, way)
            is Spot.OnRoad -> words.fromCentreAbout(near, way)
            is Spot.InSuburb -> words.fromCentreRough("%.0f".format(km), way)
        }
    }

    private const val MILES_TO_KM = 1.609344

    fun shorten(place: String): String {
        val trimmed = place
            .replace(Regex("""^\S{1,2}\s+"""), "")
            .substringBefore('(')
            .substringBefore(',')
            .trim()
            .ifEmpty { place.trim() }
        return if (trimmed.length <= MAX_NAME) trimmed else trimmed.take(MAX_NAME).trimEnd() + "…"
    }

    /** Longer than this and it stops being readable at a glance anyway. */
    private const val MAX_NAME = 14

    /** "8.1 公里" or "8.1 km", or null when the card's distance was unreadable. */
    fun distance(card: OfferCard, lang: Lang): String? {
        val miles = card.distance?.value ?: return null
        return wordsIn(lang).kmAway(String.format("%.1f", miles / MILES_PER_KM))
    }

    /** "$12.50/小时" or "$12.50/h" after petrol, or null when the card's distance or time was unreadable. */
    fun rate(card: OfferCard, cost: TripCost, lang: Lang): String? {
        val perHour = TripEarnings.perHour(card, cost) ?: return null
        return wordsIn(lang).perHourRate(String.format("%.2f", perHour))
    }

    /** The suburb a stop names, longest match first so Noble Park North wins. */
    private fun suburbOf(place: String, suburbs: List<Suburb>): String? =
        SuburbIndex.findAll(place, suburbs).firstOrNull()?.name

    private const val MILES_PER_KM = 0.621371
}
