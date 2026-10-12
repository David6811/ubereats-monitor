package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Whether the suburb the card names can be where the food is going.
 *
 * Uber writes a locality, not always a suburb. "320 Lower Dandenong Road,
 * Melbourne" is a drop three kilometres away, and Melbourne is a real entry in
 * the gazetteer twenty-five kilometres off, so the job was refused for going
 * somewhere it was not going.
 *
 * The card carries its own distance for the whole trip, which is evidence the
 * text cannot contradict: a drop cannot be further from the car than the trip
 * is long, give or take the roads. When it plainly is, the name is wrong and
 * nothing should be refused on it.
 *
 * The margin is wide on purpose. This only ever turns a refusal into "I do not
 * know", never into a yes, and a genuine run into the city is still refused:
 * the card says twenty-five kilometres, and twenty-five is not far-fetched for
 * a twenty-five kilometre trip.
 */
object SuburbDoubt {

    /** How much further than the trip the drop may appear to be before the name is doubted. */
    private const val SLACK = 1.5
    private const val MARGIN_MILES = 3.1

    fun tooFarToBe(suburb: Suburb, carAt: GeoPoint?, trip: Miles?): Boolean {
        if (carAt == null || trip == null) return false
        val away = Geo.straightLine(carAt, suburb.at).value
        return away > trip.value * SLACK + MARGIN_MILES
    }
}
