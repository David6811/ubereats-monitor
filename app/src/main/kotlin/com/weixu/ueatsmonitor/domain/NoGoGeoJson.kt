package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calculation. The boxes drawn on the laptop as GeoJSON, so the phone can show
 * them on the same map it shows the suburbs on.
 *
 * Each box is a rectangle of latitude and longitude, which is four corners and
 * back to the first. [REFUSING] says whether it is still turning offers away:
 * a box switched off for this trip stays on the map, drawn dim, because the
 * driver needs to see the one he switched off as much as the ones he did not.
 */
object NoGoGeoJson {

    const val LABEL = "label"
    const val REFUSING = "refusing"

    fun featureCollection(boxes: List<NoGoBox>, off: Set<String>): String =
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                boxes.forEach { box ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put(LABEL, box.label)
                            put(REFUSING, off.none { it.equals(box.label, ignoreCase = true) })
                        }
                        putJsonObject("geometry") {
                            put("type", "Polygon")
                            put("coordinates", buildJsonArray { add(ring(box)) })
                        }
                    })
                }
            }
        }.toString()

    /** GeoJSON order is [longitude, latitude], and a ring closes on its first point. */
    private fun ring(box: NoGoBox) = buildJsonArray {
        listOf(
            box.west to box.south,
            box.east to box.south,
            box.east to box.north,
            box.west to box.north,
            box.west to box.south,
        ).forEach { (lon, lat) ->
            add(buildJsonArray {
                add(JsonPrimitive(lon))
                add(JsonPrimitive(lat))
            })
        }
    }
}
