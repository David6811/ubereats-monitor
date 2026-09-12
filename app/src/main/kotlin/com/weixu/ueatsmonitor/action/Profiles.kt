package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.ActiveSet
import com.weixu.ueatsmonitor.domain.GeoPoint
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

    /**
     * The set in force. Whichever was chosen last wins - a tap here while driving,
     * or a push from the laptop. Neither place owns the answer; having the phone
     * always win made a change on the laptop look like it had done nothing.
     */
    fun chosen(context: Context): String? {
        val choice = File(context.filesDir, CHOICE)
        val picked = choice
            .takeIf { it.exists() }
            ?.runCatching { readText().trim() }
            ?.getOrNull()
            ?.ifEmpty { null }
        return ActiveSet.inForce(
            picked = picked,
            pickedAtMillis = if (choice.exists()) choice.lastModified() else 0,
            named = laptopActive(context),
            namedAtMillis = rulesFile(context).let { if (it.exists()) it.lastModified() else 0 },
            known = all(context).map { it.name }.toSet(),
        )
    }

    fun choose(context: Context, name: String) {
        runCatching { File(context.filesDir, CHOICE).writeText(name) }
            .onSuccess { Log.i("UEatsMonitor", "rules: using set " + name) }
            .onFailure { Log.w("UEatsMonitor", "rules: could not record the set", it) }
    }

    /** Whether the live set was chosen here or arrived from the laptop. */
    fun chosenHere(context: Context): Boolean {
        val choice = File(context.filesDir, CHOICE)
        if (!choice.exists()) return false
        val rules = rulesFile(context)
        return !rules.exists() || choice.lastModified() >= rules.lastModified()
    }

    fun list(context: Context): List<RuleProfile> {
        val here = chosen(context)
        return all(context).map { it.copy(active = it.name == here) }
    }

    /** Data. The far set as the phone needs to show it. */
    data class Far(val overDollars: Int, val suburbs: Int)

    /** What the laptop drew for a big payout, for the page to name. */
    fun far(context: Context): Far? {
        val far = root(context)?.get("far")?.jsonObject ?: return null
        val suburbs = far["suburbs"]?.jsonArray?.size ?: 0
        if (suburbs == 0) return null
        val over = far["overDollars"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 30.0
        return Far(over.toInt(), suburbs)
    }

    /**
     * Where the set in force is worked from, as marked on the laptop's map.
     *
     * Null until the driver marks one. It is his answer, not a computed middle:
     * the centre of a set's suburbs is a point in a field as often as not.
     */
    fun centre(context: Context): GeoPoint? {
        val name = chosen(context) ?: return null
        val here = root(context)?.get("profiles")?.jsonArray.orEmpty().firstOrNull { entry ->
            entry.jsonObject["name"]?.jsonPrimitive?.content == name
        } ?: return null
        val centre = here.jsonObject["centre"]?.jsonObject ?: return null
        val lat = centre["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        val lon = centre["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon)
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

    private fun rulesFile(context: Context): File = File(context.getExternalFilesDir(null), RULES)

    private fun root(context: Context): JsonObject? {
        val file = rulesFile(context)
        if (!file.exists()) return null
        return runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }
}
