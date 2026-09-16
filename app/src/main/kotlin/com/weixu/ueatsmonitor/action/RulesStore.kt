package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Cents
import com.weixu.ueatsmonitor.domain.Exclusions
import com.weixu.ueatsmonitor.domain.NoGoBox
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.TripCost
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
    private var readWithCost: Pair<TripCost, Double>? = null

    @Volatile
    private var readWithHomeward: GeoPoint? = null

    fun current(context: Context): Rules {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val stamp = if (file.exists()) file.lastModified() else 0L
        // The set picked on the phone is part of what the rules say, so switching
        // it has to invalidate the same cache a new file does.
        val profile = Profiles.chosen(context)
        val known = cached
        val excludedNow = ExclusionStore.inForce(context)
        val farNow = LiveSettings.current?.farEnabled != false
        val costNow = costOf(LiveSettings.current)
        // The set's own centre, but only while the switch is on: off, the rule
        // simply is not there.
        val homewardNow = if (LiveSettings.current?.homewardEnabled == true) Profiles.centre(context) else null
        if (known != null && stamp == readAtMillis && profile == readForProfile &&
            excludedNow == readWithExcluded && farNow == readWithFar && costNow == readWithCost &&
            homewardNow == readWithHomeward
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
                tripCost = costNow.first,
                farMinPerHour = costNow.second,
                homewardCentre = homewardNow,
            )
        }
        cached = parsed
        readAtMillis = stamp
        readForProfile = profile
        readWithExcluded = excluded
        readWithFar = farNow
        readWithCost = costNow
        readWithHomeward = homewardNow
        Log.i(
            "UEatsMonitor",
            "rules: set " + profile + ", " + parsed.allowedSuburbs.size + " suburbs, " +
                parsed.deniedStores.size + " denied stores, " +
                parsed.alwaysOkStores.size + " always ok, " +
                parsed.farSuburbs.size + " far over " + parsed.farOverCents + ", " +
                parsed.noGoBoxes.size + " no-go boxes",
        )
        return parsed
    }

    /** The phone's petrol reckoning, or the driver's defaults before settings have loaded. */
    private fun costOf(settings: SettingsStore.Settings?): Pair<TripCost, Double> = Pair(
        TripCost(
            fuelPerKm = settings?.fuelPerKm ?: SettingsStore.DEFAULT_FUEL_PER_KM,
            timeFactor = settings?.timeFactor ?: SettingsStore.DEFAULT_TIME_FACTOR,
        ),
        settings?.farMinPerHour ?: SettingsStore.DEFAULT_FAR_MIN_PER_HOUR,
    )

    private fun empty(): Rules = Rules(
        allowedSuburbs = emptySet(),
        farSuburbs = emptySet(),
        farOverCents = Cents.ofDollars(DEFAULT_FAR_DOLLARS),
        deniedStores = emptyList(),
        alwaysOkStores = emptyList(),
        noGoBoxes = emptyList(),
        tripCost = costOf(null).first,
        farMinPerHour = costOf(null).second,
        homewardCentre = null,
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
            // The set a big payout unlocks, and the payout that unlocks it. Absent
            // means the driver never drew one and the rule simply does not fire.
            val far = root["far"]?.jsonObject
            val farSuburbs = far?.get("suburbs")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
            val overDollars = far?.get("overDollars")?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: DEFAULT_FAR_DOLLARS

            // Rectangles drawn on the map. One with a missing edge is skipped
            // rather than read as zero, which would stretch it to the equator.
            val noGo = root["noGo"]?.jsonArray?.mapNotNull { entry ->
                val box = entry.jsonObject
                fun edge(key: String) = box[key]?.jsonPrimitive?.content?.toDoubleOrNull()
                val south = edge("south") ?: return@mapNotNull null
                val west = edge("west") ?: return@mapNotNull null
                val north = edge("north") ?: return@mapNotNull null
                val east = edge("east") ?: return@mapNotNull null
                NoGoBox(box["label"]?.jsonPrimitive?.content ?: "不接单区", south, west, north, east)
            }.orEmpty()

            Rules(
                allowedSuburbs = allow,
                farSuburbs = farSuburbs,
                farOverCents = Cents.ofDollars(overDollars),
                deniedStores = deny,
                alwaysOkStores = alwaysOk,
                noGoBoxes = noGo,
                // Filled from the phone's settings by current(), not the file.
                tripCost = costOf(null).first,
                farMinPerHour = costOf(null).second,
                homewardCentre = null,
            )
        }.getOrElse { empty() }
    }
}
