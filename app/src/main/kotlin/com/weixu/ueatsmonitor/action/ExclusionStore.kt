package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Excluded
import com.weixu.ueatsmonitor.domain.Exclusions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * Action. The suburbs ticked off on the phone, in the app's own storage.
 *
 * rules.json cannot be written here - adb push leaves it owned by shell - and it
 * should not be: these are a change of mind for one shift, not a rule.
 */
object ExclusionStore {

    private const val FILE_NAME = "excluded.json"
    private val json = Json { ignoreUnknownKeys = true }

    /** What is excluded right now, which is nothing once the set or the rules change. */
    fun inForce(context: Context): Set<String> =
        Exclusions.inForce(read(context), Profiles.chosen(context), stampOf(context))

    fun toggle(context: Context, suburb: String) {
        val set = Profiles.chosen(context) ?: return
        val next = Exclusions.toggle(inForce(context), suburb)
        write(context, Excluded(set, stampOf(context), next))
    }

    /** Drops every suburb of the live set but this one. */
    fun keepOnly(context: Context, suburb: String, all: List<String>) {
        val set = Profiles.chosen(context) ?: return
        write(context, Excluded(set, stampOf(context), Exclusions.keepOnly(all.toSet(), suburb)))
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    /** The rules file's own timestamp, which a push moves. */
    private fun stampOf(context: Context): Long =
        File(context.getExternalFilesDir(null), "rules.json")
            .takeIf { it.exists() }
            ?.lastModified()
            ?: 0L

    private fun read(context: Context): Excluded? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            Excluded(
                set = root["set"]!!.jsonPrimitive.content,
                stamp = root["stamp"]!!.jsonPrimitive.long,
                suburbs = root["suburbs"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            )
        }.getOrNull()
    }

    private fun write(context: Context, excluded: Excluded) {
        val body = buildJsonObject {
            put("set", JsonPrimitive(excluded.set))
            put("stamp", JsonPrimitive(excluded.stamp))
            put("suburbs", buildJsonArray { excluded.suburbs.forEach { add(JsonPrimitive(it)) } })
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(body.toString()) }
            .onFailure { Log.w("UEatsMonitor", "excluded: could not write", it) }
    }
}
