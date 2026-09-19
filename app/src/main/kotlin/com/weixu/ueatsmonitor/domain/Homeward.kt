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
 * Data. The homeward rule as the driver set it: where the middle is, how near
 * to it a drop may land and still be taken even if it leads away, and how
 * long a job may be when there is little of the shift left.
 */
data class HomewardLimits(
    val centre: GeoPoint,
    /** A drop this close to the centre is taken whether or not it leads away. */
    val nearKm: Double,
    /** A job the card says takes longer than this is left, however near. */
    val maxMinutes: Int,
)

/**
 * Data. The near-centre rule as the driver set it: stay within [maxKm] of the
 * middle, on jobs of at most [maxMinutes]. Unlike [HomewardLimits] it does not
 * care where the car is now - only where the drop lands.
 */
data class NearCentreLimits(
    val centre: GeoPoint,
    val maxKm: Double,
    val maxMinutes: Int,
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
