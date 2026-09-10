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

/**
 * Action. Writes the chosen suburbs back into rules.json on the phone.
 *
 * Everything else in the file - the denied stores, the shopping-strip expansion,
 * the other sets - is left exactly as the laptop wrote it. The active set is
 * updated alongside suburbs.allow, the same pair the laptop keeps in step, so a
 * change made in the car survives the next save from the laptop.
 */
object RulesWriter {

    private const val FILE_NAME = "rules.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun allowedSuburbs(context: Context): Set<String> {
        val root = read(context) ?: return emptySet()
        return root["suburbs"]?.jsonObject?.get("allow")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?.toSet()
            .orEmpty()
    }

    /** The name of the set being edited, for the page to show. */
    fun activeProfile(context: Context): String? =
        read(context)?.get("active")?.jsonPrimitive?.content

    fun setAllowedSuburbs(context: Context, suburbs: Set<String>): Boolean {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val root = read(context) ?: return false
        val names = JsonArray(suburbs.sorted().map(::JsonPrimitive))
        val active = root["active"]?.jsonPrimitive?.content

        val next = buildJsonObject {
            for ((key, value) in root) {
                when (key) {
                    "suburbs" -> put(key, withAllow(value, names))
                    "profiles" -> put(key, withActiveSet(value, active, names))
                    else -> put(key, value)
                }
            }
        }
        return runCatching {
            file.writeText(json.encodeToString(JsonObject.serializer(), next))
            Log.i("UEatsMonitor", "rules: wrote " + suburbs.size + " suburbs from the phone")
            true
        }.getOrElse {
            Log.w("UEatsMonitor", "rules: could not write " + file, it)
            false
        }
    }

    private fun withAllow(suburbs: JsonElement, names: JsonArray): JsonObject =
        buildJsonObject {
            for ((key, value) in suburbs.jsonObject) if (key != "allow") put(key, value)
            put("allow", names)
        }

    private fun withActiveSet(profiles: JsonElement, active: String?, names: JsonArray): JsonArray =
        JsonArray(
            profiles.jsonArray.map { entry ->
                val profile = entry.jsonObject
                if (profile["name"]?.jsonPrimitive?.content != active) entry
                else buildJsonObject {
                    for ((key, value) in profile) if (key != "suburbs") put(key, value)
                    put("suburbs", names)
                }
            }
        )

    private fun read(context: Context): JsonObject? {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        if (!file.exists()) return null
        return runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }
}
