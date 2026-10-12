package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calculation. The bundled suburb outlines as GeoJSON for the map, by name.
 *
 * Every suburb goes in, chosen or not: the map draws the rest as faint lines so
 * the set reads against its neighbours, and a tap adds them. Each ring becomes
 * its own polygon of a MultiPolygon - the bundled rings are separate pieces of
 * a suburb, not holes in it.
 *
 * Which ones are in the set is NOT written here. It was, and every tap then
 * rebuilt all 760 outlines - a megabyte of text off 50,000 points - on the
 * thread drawing the screen, which measured 290ms of nothing happening under
 * the finger. The names alone never change, so they are handed over once and
 * the set is drawn by filtering the layers on this name.
 */
object SuburbGeoJson {

    const val NAME = "name"

    fun featureCollection(shapes: List<SuburbShape>): String =
        buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                shapes.forEach { shape ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") { put(NAME, shape.name) }
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
