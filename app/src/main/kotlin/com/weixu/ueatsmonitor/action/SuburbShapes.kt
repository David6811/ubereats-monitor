package com.weixu.ueatsmonitor.action

import android.content.Context
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.SuburbShape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Action. Reads the bundled suburb outlines off disk once, then never again.
 *
 * The asset is the same OpenStreetMap boundaries the laptop editor draws, thinned
 * to about sixty points a suburb - enough to tell Noble Park from Keysborough at
 * the size of a phone, and a megabyte instead of two.
 */
object SuburbShapes {

    private const val ASSET = "suburb-shapes.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var loaded: List<SuburbShape>? = null

    fun all(context: Context): List<SuburbShape> = loaded ?: synchronized(this) {
        loaded ?: load(context).also { loaded = it }
    }

    private fun load(context: Context): List<SuburbShape> = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        json.parseToJsonElement(text).jsonArray.map { entry ->
            val shape = entry.jsonObject
            SuburbShape(
                name = shape["n"]!!.jsonPrimitive.content,
                rings = shape["r"]!!.jsonArray.map { ring ->
                    ring.jsonArray.map { pair ->
                        val point = pair.jsonArray
                        // Stored as [lon, lat], the order GeoJSON uses.
                        GeoPoint(point[1].jsonPrimitive.double, point[0].jsonPrimitive.double)
                    }
                },
            )
        }
    }.getOrDefault(emptyList())
}
