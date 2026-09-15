package com.weixu.ueatsmonitor.domain

/**
 * Data. What a trip costs beyond the time on the card, as the driver reckons it.
 *
 * [fuelPerKm] is dollars of petrol per kilometre; the card's distance is driven
 * twice, out and back. [timeFactor] stretches the card's minutes to cover the
 * way back.
 */
data class TripCost(
    val fuelPerKm: Double,
    val timeFactor: Double,
)

/** Calculation. Dollars an hour left after petrol, by the driver's own formula. */
object TripEarnings {

    private const val MILES_PER_KM = 0.621371

    /**
     * (payout - km x 2 x fuelPerKm) / (minutes x timeFactor / 60).
     * Null when the card's distance or minutes were unreadable: a guess here
     * would refuse or pass a job on nothing.
     */
    fun perHour(card: OfferCard, cost: TripCost): Double? {
        val km = (card.distance?.value ?: return null) / MILES_PER_KM
        val minutes = card.duration?.value ?: return null
        val hours = minutes * cost.timeFactor / 60.0
        if (hours <= 0.0) return null
        return (card.payout.dollars - km * 2 * cost.fuelPerKm) / hours
    }
}
