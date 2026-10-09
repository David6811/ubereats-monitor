package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calculation. The bundled suburb outlines as GeoJSON for the map, each one
 * marked with whether it is in the set.
 *
 * Every suburb goes in, chosen or not: the map draws the rest as faint lines so
 * the set reads against its neighbours, and a later step taps them in. Each ring
 * becomes its own polygon of a MultiPolygon - the bundled rings are separate
 * pieces of a suburb, not holes in it.
 */
object SuburbGeoJson {

    const val NAME = "name"
    const val CHOSEN = "chosen"

    fun featureCollection(shapes: List<SuburbShape>, chosen: Set<String>): String =
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                shapes.forEach { shape ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") {
                            put(NAME, shape.name)
                            put(CHOSEN, shape.name in chosen)
                        }
                        putJsonObject("geometry") {
                            put("type", "MultiPolygon")
                            put("coordinates", buildJsonArray {
                                shape.rings.forEach { ring -> add(buildJsonArray { add(positions(ring)) }) }
                            })
                        }
                    })
                }
            }
        }.toString()

    /** GeoJSON order is [longitude, latitude]. */
    private fun positions(ring: List<GeoPoint>): JsonArray = buildJsonArray {
        ring.forEach { point ->
            add(buildJsonArray {
                add(JsonPrimitive(point.longitude))
                add(JsonPrimitive(point.latitude))
            })
        }
    }
}
