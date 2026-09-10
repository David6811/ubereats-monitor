package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Data. One of the sets of suburbs drawn on the laptop. */
data class RuleProfile(val name: String, val suburbs: List<String>, val active: Boolean)

/**
 * Action. The sets of suburbs, and which one the driver picked on the phone.
 *
 * The sets are drawn on the laptop and arrive in rules.json. That file cannot be
 * rewritten here - adb push leaves it owned by shell, and the app is refused
 * (EACCES) - so the choice is kept in the app's own storage instead, and read
 * back alongside the file. Which is the better shape anyway: the laptop's file
 * stays the laptop's.
 */
object Profiles {

    private const val RULES = "rules.json"
    private const val CHOICE = "profile.txt"
    private val json = Json { ignoreUnknownKeys = true }

    /** The set in force: the one picked here, or the one the laptop marked active. */
    fun chosen(context: Context): String? {
        val picked = File(context.filesDir, CHOICE)
            .takeIf { it.exists() }
            ?.runCatching { readText().trim() }
            ?.getOrNull()
            ?.ifEmpty { null }
        val known = all(context).map { it.name }
        return picked?.takeIf { it in known } ?: laptopActive(context)
    }

    fun choose(context: Context, name: String) {
        runCatching { File(context.filesDir, CHOICE).writeText(name) }
            .onSuccess { Log.i("UEatsMonitor", "rules: using set " + name) }
            .onFailure { Log.w("UEatsMonitor", "rules: could not record the set", it) }
    }

    fun list(context: Context): List<RuleProfile> {
        val here = chosen(context)
        return all(context).map { it.copy(active = it.name == here) }
    }

    /** The suburbs of the set in force, or null when there are no sets at all. */
    fun suburbsInForce(context: Context): Set<String>? {
        val name = chosen(context) ?: return null
        return all(context).firstOrNull { it.name == name }?.suburbs?.toSet()
    }

    private fun all(context: Context): List<RuleProfile> =
        root(context)?.get("profiles")?.jsonArray.orEmpty().mapNotNull { entry ->
            val profile = entry.jsonObject
            val name = profile["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RuleProfile(
                name = name,
                suburbs = profile["suburbs"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                active = false,
            )
        }

    private fun laptopActive(context: Context): String? =
        root(context)?.get("active")?.jsonPrimitive?.content

    private fun root(context: Context): JsonObject? {
        val file = File(context.getExternalFilesDir(null), RULES)
        if (!file.exists()) return null
        return runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }
}
