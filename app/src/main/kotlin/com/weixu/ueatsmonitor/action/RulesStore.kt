package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Rules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Action. Reads rules.json, the file pushed from the laptop.
 *
 * Re-read whenever it changes on disk, so a push takes effect without touching
 * the phone. Absent or broken means no rules at all - the app says so rather
 * than falling back on numbers of its own.
 */
object RulesStore {

    private const val FILE_NAME = "rules.json"
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: Rules? = null

    @Volatile
    private var readAtMillis: Long = 0

    fun current(context: Context): Rules {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val stamp = if (file.exists()) file.lastModified() else 0L
        val known = cached
        if (known != null && stamp == readAtMillis) return known

        val parsed = parse(file)
        cached = parsed
        readAtMillis = stamp
        Log.i(
            "UEatsMonitor",
            "rules: " + parsed.allowedSuburbs.size + " suburbs, " +
                parsed.deniedStores.size + " denied stores",
        )
        return parsed
    }

    private fun parse(file: File): Rules {
        if (!file.exists()) return Rules(emptySet(), emptyList())
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val allow = root["suburbs"]?.jsonObject?.get("allow")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
            val deny = root["stores"]?.jsonObject?.get("deny")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty()
            Rules(allow, deny)
        }.getOrElse { Rules(emptySet(), emptyList()) }
    }
}
