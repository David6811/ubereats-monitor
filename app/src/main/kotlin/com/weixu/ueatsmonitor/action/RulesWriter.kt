package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Data. One of the sets of suburbs drawn on the laptop. */
data class RuleProfile(val name: String, val suburbs: Int, val active: Boolean)

/**
 * Action. Switches rules.json between the sets the laptop drew.
 *
 * The sets themselves are made on the laptop, on the map. The phone only picks
 * which one is live, which is the one thing that changes mid-shift.
 */
object RulesWriter {

    private const val FILE_NAME = "rules.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun profiles(context: Context): List<RuleProfile> {
        val root = read(context) ?: return emptyList()
        val active = root["active"]?.jsonPrimitive?.content
        return root["profiles"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val profile = entry.jsonObject
            val name = profile["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RuleProfile(
                name = name,
                suburbs = profile["suburbs"]?.jsonArray?.size ?: 0,
                active = name == active,
            )
        }
    }

    /** Makes [name] the live set: it becomes active, and its suburbs become the allow list. */
    fun activate(context: Context, name: String): Boolean {
        val root = read(context) ?: return false
        val chosen = root["profiles"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content == name }
            ?: return false
        val names = chosen["suburbs"]?.jsonArray ?: JsonArray(emptyList())

        val next = buildJsonObject {
            for ((key, value) in root) {
                when (key) {
                    "active" -> put(key, JsonPrimitive(name))
                    "suburbs" -> put(key, withAllow(value, names))
                    else -> put(key, value)
                }
            }
        }
        return runCatching {
            File(context.getExternalFilesDir(null), FILE_NAME)
                .writeText(json.encodeToString(JsonObject.serializer(), next))
            Log.i("UEatsMonitor", "rules: switched to " + name + ", " + names.size + " suburbs")
            true
        }.getOrElse {
            Log.w("UEatsMonitor", "rules: could not switch to " + name, it)
            false
        }
    }

    private fun withAllow(suburbs: JsonElement, names: JsonArray): JsonObject =
        buildJsonObject {
            for ((key, value) in suburbs.jsonObject) if (key != "allow") put(key, value)
            put("allow", names)
        }

    private fun read(context: Context): JsonObject? {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        if (!file.exists()) return null
        return runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }
}
