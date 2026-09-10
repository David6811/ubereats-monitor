package com.weixu.ueatsmonitor.domain

/**
 * Calculation. The two lines under the verdict on the offer card.
 *
 * Only the suburbs, because a street name is unreadable in the second the card
 * gives you, and only the rate, because the payout on its own says nothing until
 * it is divided by the distance.
 */
object ChipText {

    /** "Springvale（快餐） → Mulgrave", or null when neither end was recognised. */
    fun route(card: OfferCard, suburbs: List<Suburb>, stores: List<Store>): String? {
        val from = suburbOf(card.pickup, suburbs)
        val to = suburbOf(card.dropoff, suburbs)
        if (from == null && to == null) return null
        val kind = StoreKinds.of(card.pickup, stores)?.let { "（" + StoreKinds.label(it) + "）" } ?: ""
        return (from ?: "?") + kind + " → " + (to ?: "?")
    }

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
