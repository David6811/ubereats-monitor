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
