package com.weixu.ueatsmonitor.action

import android.content.Context
import com.weixu.ueatsmonitor.domain.Brief
import com.weixu.ueatsmonitor.domain.StopBrief
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * Action. Reads brief.json, the file the laptop pushes when it has looked at the
 * current offer. Missing means no laptop is listening, which is the usual state.
 */
object BriefStore {

    private const val FILE_NAME = "brief.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun current(context: Context): Brief? {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            Brief(
                atMillis = root["at"]?.jsonPrimitive?.long ?: file.lastModified(),
                pickup = stop(root, "pickup"),
                dropoff = stop(root, "dropoff"),
            )
        }.getOrNull()
    }

    private fun stop(root: JsonObject, key: String): StopBrief? {
        val stop = root[key]?.jsonObject ?: return null
        val place = stop["place"]?.jsonPrimitive?.content ?: return null
        val advice = stop["advice"]?.jsonPrimitive?.content ?: return null
        return StopBrief(place, advice)
    }
}
