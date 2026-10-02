package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.BoxesOff
import com.weixu.ueatsmonitor.domain.NoGoOff
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
 * Action. The no-go boxes switched off on the phone, in the app's own storage.
 *
 * Beside [ExclusionStore] and for the same reason: rules.json cannot be written
 * here, and these are not rules anyway.
 */
object NoGoOffStore {

    private const val FILE_NAME = "nogo-off.json"
    private val json = Json { ignoreUnknownKeys = true }

    /** Which boxes are off right now, which is none once the rules change. */
    fun inForce(context: Context): Set<String> = NoGoOff.inForce(read(context), stampOf(context))

    fun toggle(context: Context, label: String) {
        write(context, BoxesOff(stampOf(context), NoGoOff.toggle(inForce(context), label)))
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

    private fun read(context: Context): BoxesOff? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            BoxesOff(
                stamp = root["stamp"]!!.jsonPrimitive.long,
                labels = root["labels"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            )
        }.getOrNull()
    }

    private fun write(context: Context, off: BoxesOff) {
        val body = buildJsonObject {
            put("stamp", JsonPrimitive(off.stamp))
            put("labels", buildJsonArray { off.labels.forEach { add(JsonPrimitive(it)) } })
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(body.toString()) }
            .onFailure { Log.w("UEatsMonitor", "nogo-off: could not write", it) }
    }
}
