package com.weixu.ueatsmonitor.domain

/**
 * Data. A rectangle drawn on the laptop's map: nothing is picked up or delivered
 * inside it. Edges run due north-south and east-west, as the editor draws them.
 */
data class NoGoBox(
    val label: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

/**
 * Data. Where the two stops of a card are, as far as the app could place them.
 * Null when it could not: the shop is not in the table, or the dropoff named no
 * street it knows.
 */
data class Stops(
    val pickup: Store?,
    val dropoff: Spot?,
) {
    companion object {
        val UNPLACED = Stops(pickup = null, dropoff = null)
    }
}

/** Data. Which stop fell in which box. */
sealed interface NoGoHit {
    val box: NoGoBox

    data class Pickup(override val box: NoGoBox, val store: String) : NoGoHit
    data class Dropoff(override val box: NoGoBox) : NoGoHit
}

/**
 * Calculation. Whether either stop of a card falls inside a no-go box.
 *
 * A dropoff placed only by its suburb is not checked: its point is the middle
 * of the suburb, kilometres from the door, and a box drawn along a road would
 * refuse or pass it by accident. The suburb list still judges those.
 */
object NoGo {

    fun hit(boxes: List<NoGoBox>, stops: Stops): NoGoHit? {
        if (boxes.isEmpty()) return null
        val pickup = stops.pickup
        if (pickup != null) {
            boxes.firstOrNull { contains(it, pickup.at) }?.let { return NoGoHit.Pickup(it, pickup.name) }
        }
        val dropoff = stops.dropoff
        if (dropoff != null && dropoff !is Spot.InSuburb) {
            boxes.firstOrNull { contains(it, dropoff.at) }?.let { return NoGoHit.Dropoff(it) }
        }
        return null
    }

    fun contains(box: NoGoBox, point: GeoPoint): Boolean =
        point.latitude in box.south..box.north && point.longitude in box.west..box.east
}
