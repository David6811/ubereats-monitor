package com.weixu.ueatsmonitor.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Data. The outline of one suburb, as one or more closed rings. */
data class SuburbShape(val name: String, val rings: List<List<GeoPoint>>)

/** Data. A point on the drawing, in pixels from its top left. */
data class Pixel(val x: Float, val y: Float)

/** Data. The corner of the world a drawing covers. */
data class GeoBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

/**
 * Calculation. Fits suburb outlines onto a canvas.
 *
 * Longitude is squeezed by the cosine of the latitude before anything is fitted,
 * because a degree of longitude is only about four fifths of a degree of latitude
 * this far south; without it every suburb comes out stretched sideways. What is
 * left is a plain fit that keeps the aspect ratio and centres what is left over.
 */
object MapProjection {

    fun boxOf(shapes: List<SuburbShape>): GeoBox? {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var seen = false
        for (shape in shapes) for (ring in shape.rings) for (point in ring) {
            seen = true
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }
        return if (seen) GeoBox(minLat, maxLat, minLon, maxLon) else null
    }

    /**
     * How many canvas pixels one degree of latitude is worth, and where the fitted
     * drawing starts. Worked out once per frame and reused for every point.
     */
    data class Fit(val box: GeoBox, val scale: Float, val left: Float, val top: Float) {

        fun place(point: GeoPoint): Pixel = Pixel(
            x = left + ((point.longitude - box.minLon) * squeeze(box) * scale).toFloat(),
            y = top + ((box.maxLat - point.latitude) * scale).toFloat(),
        )
    }

    fun fit(box: GeoBox, width: Float, height: Float, padding: Float): Fit {
        val spanLat = (box.maxLat - box.minLat).coerceAtLeast(MIN_SPAN)
        val spanLon = (box.maxLon - box.minLon).coerceAtLeast(MIN_SPAN) * squeeze(box)
        val usableWidth = (width - padding * 2).coerceAtLeast(1f)
        val usableHeight = (height - padding * 2).coerceAtLeast(1f)
        val scale = min(usableWidth / spanLon, usableHeight / spanLat).toFloat()
        return Fit(
            box = box,
            scale = scale,
            left = padding + (usableWidth - (spanLon * scale).toFloat()) / 2,
            top = padding + (usableHeight - (spanLat * scale).toFloat()) / 2,
        )
    }

    /** A degree of longitude is shorter than one of latitude, by this much. */
    private fun squeeze(box: GeoBox): Double =
        cos(Math.toRadians((box.minLat + box.maxLat) / 2))

    /** A single suburb still needs a span, or the scale is infinite. */
    private const val MIN_SPAN = 1e-6
}
