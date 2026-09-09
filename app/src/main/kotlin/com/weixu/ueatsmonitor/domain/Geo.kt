package com.weixu.ueatsmonitor.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Data. A point on the earth. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Data. One named place from the offline gazetteer. */
data class Suburb(val name: String, val at: GeoPoint)

/** Data. The eight directions a driver actually thinks in. */
enum class Compass(val label: String) {
    N("正北"), NE("东北"), E("正东"), SE("东南"),
    S("正南"), SW("西南"), W("正西"), NW("西北"),
}

/** Data. Where a place is, seen from where you stand. */
data class Heading(val bearingDegrees: Double, val compass: Compass, val straightLine: Miles)

/**
 * Calculation. Great-circle bearing and distance. No network, no map tiles -
 * two coordinates are all a direction needs.
 */
object Geo {

    private const val EARTH_RADIUS_MILES = 3958.7613

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun straightLine(from: GeoPoint, to: GeoPoint): Miles {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val halfLat = Math.toRadians(to.latitude - from.latitude) / 2
        val halfLon = Math.toRadians(to.longitude - from.longitude) / 2
        val h = sin(halfLat) * sin(halfLat) + cos(lat1) * cos(lat2) * sin(halfLon) * sin(halfLon)
        return Miles(2 * EARTH_RADIUS_MILES * asin(sqrt(h)))
    }

    fun compassOf(bearingDegrees: Double): Compass {
        val index = ((bearingDegrees % 360.0 + 360.0) % 360.0 / 45.0).roundToInt() % 8
        return Compass.entries[index]
    }

    fun headingTo(from: GeoPoint, to: GeoPoint): Heading {
        val bearing = bearingDegrees(from, to)
        return Heading(
            bearingDegrees = bearing,
            compass = compassOf(bearing),
            straightLine = straightLine(from, to),
        )
    }
}
