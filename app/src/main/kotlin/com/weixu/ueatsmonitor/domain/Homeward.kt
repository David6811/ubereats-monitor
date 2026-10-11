package com.weixu.ueatsmonitor.domain

/**
 * Data. What the homeward rule made of a card.
 *
 * [Unknown] is its own case rather than a silent pass: without the car's own
 * position, or without a place for the drop, the rule has nothing to say, and
 * saying nothing is not the same as saying yes.
 */
sealed interface Homeward {
    data class Closer(val fromCar: Miles, val fromDrop: Miles) : Homeward
    data class Further(val fromCar: Miles, val fromDrop: Miles) : Homeward
    data object Unknown : Homeward
}

/**
 * Data. The two ways of working around a centre. Both of them read the same
 * distance - how far the drop lands from the middle - and differ only in what
 * they do with it.
 */
enum class CentreMode {
    /**
     * Refuse a drop that leaves him further from the middle than he is now,
     * unless it lands within the radius anyway. Needs the car's position, and
     * says nothing without it.
     */
    HEAD_BACK,

    /**
     * Refuse a drop outside the radius, wherever the car happens to be. Works
     * with no position at all, which is when the other one gives up.
     */
    STAY_NEAR,
}

/**
 * Data. The centre rule as the driver set it: where the middle is, how many
 * kilometres count as near it, how long a job may be, and which of the two
 * readings of that radius is in force.
 */
data class CentreLimits(
    val centre: GeoPoint,
    /**
     * Heading back, a drop this close is taken whether or not it leads away.
     * Staying near, it is the furthest a drop may land.
     */
    val nearKm: Double,
    /**
     * A job the card says takes longer than this is left, however near. Null
     * leaves the length alone: a driver with the whole night ahead of him does
     * not care how long one job takes, only where it ends.
     */
    val maxMinutes: Int?,
    val mode: CentreMode,
)
/**
 * Calculation. Whether taking this job would leave the driver nearer the centre
 * of his set than he is now.
 *
 * For the end of a shift, and for the drive back from a long one: the suburb
 * rules still decide where he may go, and this only refuses what would take him
 * further out. Straight-line distance, which is all two points can say - the
 * roads may disagree, and the difference is not worth a map.
 */
object HomewardRule {

    fun judge(carAt: GeoPoint?, dropAt: GeoPoint?, centre: GeoPoint?): Homeward {
        if (carAt == null || dropAt == null || centre == null) return Homeward.Unknown
        val fromCar = Geo.straightLine(carAt, centre)
        val fromDrop = Geo.straightLine(dropAt, centre)
        return if (fromDrop.value < fromCar.value) {
            Homeward.Closer(fromCar, fromDrop)
        } else {
            Homeward.Further(fromCar, fromDrop)
        }
    }
}
