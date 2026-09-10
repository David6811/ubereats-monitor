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

    @Volatile
    private var readForProfile: String? = null

    fun current(context: Context): Rules {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val stamp = if (file.exists()) file.lastModified() else 0L
        // The set picked on the phone is part of what the rules say, so switching
        // it has to invalidate the same cache a new file does.
        val profile = Profiles.chosen(context)
        val known = cached
        if (known != null && stamp == readAtMillis && profile == readForProfile) return known

        val parsed = parse(file).let { rules ->
            val chosen = Profiles.suburbsInForce(context)
            if (chosen == null) rules else rules.copy(allowedSuburbs = chosen)
        }
        cached = parsed
        readAtMillis = stamp
        readForProfile = profile
        Log.i(
            "UEatsMonitor",
            "rules: set " + profile + ", " + parsed.allowedSuburbs.size + " suburbs, " +
                parsed.deniedStores.size + " denied stores, " +
                parsed.alwaysOkStores.size + " always ok",
        )
        return parsed
    }

    private fun parse(file: File): Rules {
        if (!file.exists()) return Rules(emptySet(), emptyList(), emptyList())
        return runCatching {
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val allow = root["suburbs"]?.jsonObject?.get("allow")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
            // Two lists, one meaning: names typed by hand, and the shopping-strip
            // rule expanded on the laptop. Kept apart there so either can be
            // changed without disturbing the other.
            val stores = root["stores"]?.jsonObject
            val deny = listOf("deny", "cbdDeny").flatMap { key ->
                stores?.get(key)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            }.distinct()
            val alwaysOk = stores?.get("alwaysOk")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty()
            Rules(allow, deny, alwaysOk)
        }.getOrElse { Rules(emptySet(), emptyList(), emptyList()) }
    }
}
