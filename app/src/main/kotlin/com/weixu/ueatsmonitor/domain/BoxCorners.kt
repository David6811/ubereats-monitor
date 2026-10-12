package com.weixu.ueatsmonitor.domain

/**
 * Calculation. Moving one corner of a box.
 *
 * The corners are numbered anticlockwise from the south-west, the order they
 * are drawn in. Dragging one past the far side turns the box inside out, so
 * the edges are sorted afterwards: south stays south of north whatever the
 * finger did.
 */
object BoxCorners {

    const val SOUTH_WEST = 0
    const val SOUTH_EAST = 1
    const val NORTH_EAST = 2
    const val NORTH_WEST = 3

    fun moved(box: NoGoBox, corner: Int, lat: Double, lon: Double): NoGoBox {
        val lats = if (corner == SOUTH_WEST || corner == SOUTH_EAST) listOf(lat, box.north) else listOf(lat, box.south)
        val lons = if (corner == SOUTH_WEST || corner == NORTH_WEST) listOf(lon, box.east) else listOf(lon, box.west)
        return box.copy(south = lats.min(), north = lats.max(), west = lons.min(), east = lons.max())
    }
}
