package com.weixu.ueatsmonitor.domain

/**
 * Calculation. The two lines under the verdict on the offer card.
 *
 * Only the suburbs, because a street name is unreadable in the second the card
 * gives you, and only the rate, because the payout on its own says nothing until
 * it is divided by the distance.
 */
object ChipText {

    /** "Springvale（快餐） → Mulgrave". Never null: an unknown end names itself. */
    fun route(card: OfferCard, suburbs: List<Suburb>, stores: List<Store>): String {
        val from = suburbOf(card.pickup, suburbs) ?: shorten(card.pickup)
        val to = suburbOf(card.dropoff, suburbs) ?: shorten(card.dropoff)
        val shop = StoreKinds.find(card.pickup, stores)
        val kind = shop?.let { found ->
            listOfNotNull(StoreKinds.label(found.kind), StoreKinds.where(found.setting))
                .joinToString("·", prefix = "（", postfix = "）")
        } ?: ""
        return from + kind + " → " + to
    }

    /**
     * What to call a stop whose suburb we do not know: the shop's own name, as
     * short as it can be said. OCR leaves a scrap of the icon in front, the
     * suburb sits in brackets behind, and an address carries on past its first
     * comma - none of that survives.
     */
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

    /** "$1.42/公里", or null when the card's distance was unreadable. */
    fun rate(card: OfferCard): String? {
        val miles = card.distance?.value ?: return null
        val km = miles / MILES_PER_KM
        if (km <= 0) return null
        return "$" + String.format("%.2f", card.payout.dollars / km) + "/公里"
    }

    /** The suburb a stop names, longest match first so Noble Park North wins. */
    private fun suburbOf(place: String, suburbs: List<Suburb>): String? =
        SuburbIndex.findAll(place, suburbs).firstOrNull()?.name

    private const val MILES_PER_KM = 0.621371
}
