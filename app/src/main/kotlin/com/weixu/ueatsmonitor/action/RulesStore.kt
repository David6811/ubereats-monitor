package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Cents
import com.weixu.ueatsmonitor.domain.Exclusions
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

    /** What counts as a big payout when the file does not say. */
    private const val DEFAULT_FAR_DOLLARS = 30.0
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: Rules? = null

    @Volatile
    private var readAtMillis: Long = 0

    @Volatile
    private var readForProfile: String? = null

    @Volatile
    private var readWithExcluded: Set<String> = emptySet()

    @Volatile
    private var readWithFar: Boolean = true

    @Volatile
    private var readWithLanes: Boolean = true

    fun current(context: Context): Rules {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val stamp = if (file.exists()) file.lastModified() else 0L
        // The set picked on the phone is part of what the rules say, so switching
        // it has to invalidate the same cache a new file does.
        val profile = Profiles.chosen(context)
        val known = cached
        val excludedNow = ExclusionStore.inForce(context)
        val farNow = LiveSettings.current?.farEnabled != false
        val lanesNow = LiveSettings.current?.refuseLanesEnabled != false
        if (known != null && stamp == readAtMillis && profile == readForProfile &&
            excludedNow == readWithExcluded && farNow == readWithFar &&
            lanesNow == readWithLanes
        ) {
            return known
        }

        val excluded = ExclusionStore.inForce(context)
        val parsed = parse(file).let { rules ->
            val chosen = Profiles.suburbsInForce(context)
            val allow = chosen ?: rules.allowedSuburbs
            // Ticked off on the phone means not going there - including on the
            // money that would otherwise reach further.
            // Switched off, the far set is simply not there, which is what an
            // empty one already means to the judge.
            val far = if (LiveSettings.current?.farEnabled == false) emptySet()
            else Exclusions.apply(rules.farSuburbs, excluded)
            rules.copy(
                allowedSuburbs = Exclusions.apply(allow, excluded),
                farSuburbs = far,
                refuseLanes = lanesNow,
            )
        }
        cached = parsed
        readAtMillis = stamp
        readForProfile = profile
        readWithExcluded = excluded
        readWithFar = farNow
        readWithLanes = lanesNow
        Log.i(
            "UEatsMonitor",
            "rules: set " + profile + ", " + parsed.allowedSuburbs.size + " suburbs, " +
                parsed.deniedStores.size + " denied stores, " +
                parsed.alwaysOkStores.size + " always ok, " +
                parsed.farSuburbs.size + " far over " + parsed.farOverCents,
        )
        return parsed
    }

    private fun empty(): Rules = Rules(
        allowedSuburbs = emptySet(),
        farSuburbs = emptySet(),
        farOverCents = Cents.ofDollars(DEFAULT_FAR_DOLLARS),
        deniedStores = emptyList(),
        alwaysOkStores = emptyList(),
        deniedAddresses = emptyList(),
        refuseLanes = true,
    )

    private fun parse(file: File): Rules {
        if (!file.exists()) return empty()
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
            // Fragments of a destination typed by hand on the laptop.
            val deniedAddresses = root["addresses"]?.jsonObject?.get("deny")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            // The set a big payout unlocks, and the payout that unlocks it. Absent
            // means the driver never drew one and the rule simply does not fire.
            val far = root["far"]?.jsonObject
            val farSuburbs = far?.get("suburbs")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
            val overDollars = far?.get("overDollars")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: DEFAULT_FAR_DOLLARS

            Rules(
                allowedSuburbs = allow,
                farSuburbs = farSuburbs,
                farOverCents = Cents.ofDollars(overDollars),
                deniedStores = deny,
                alwaysOkStores = alwaysOk,
                deniedAddresses = deniedAddresses,
                refuseLanes = true,
            )
        }.getOrElse { empty() }
    }
}
