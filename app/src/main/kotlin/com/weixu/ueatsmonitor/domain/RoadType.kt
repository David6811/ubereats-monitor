package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Whether the card's destination is a lane or a highway.
 *
 * Both are refused, for two different reasons that point the same way. A lane in
 * Melbourne is the back of a shopping strip, and what is delivered to there is a
 * flat above a shop - the card never says so, and nothing else on it can be made
 * to. A highway is a highway: stopping on one to find a door is worth more than
 * the fare.
 *
 * It is the destination's own road type, read off the card's cross street, so it
 * survives the card naming an intersection a few hundred metres from the door -
 * a lane's neighbourhood is a lane's neighbourhood either way.
 */
object RoadType {

    /** Both the written-out word and the form Uber abbreviates it to. */
    private val REFUSED = Regex("""\b(lane|ln|highway|hwy)\b""", RegexOption.IGNORE_CASE)

    /** The word that refuses this destination, or null when none does. */
    fun refusedIn(dropoff: String): String? =
        REFUSED.find(dropoff)?.value?.replaceFirstChar { it.uppercase() }
}
