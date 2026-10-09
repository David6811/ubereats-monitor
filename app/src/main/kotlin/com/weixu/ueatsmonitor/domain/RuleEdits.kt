package com.weixu.ueatsmonitor.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Calculation. Every change the phone can make to the rules, as a function
 * from the whole rules object to the next one.
 *
 * A set lives under "profiles". "places" beside it is the laptop's list of
 * saved addresses for its centre picker - reading the sets from that one
 * found none at all on a real phone - and nothing here touches it.
 *
 * The rules are the laptop editor's own JSON and have to stay exactly that:
 * the same file is read by the laptop, by the judge, and by whichever of the
 * two saved last. So each of these touches one key and copies the rest
 * through, including keys this app has never heard of.
 */
object RuleEdits {

    // -- the shops refused by name -------------------------------------------

    fun deniedStores(rules: JsonObject): List<String> =
        rules["stores"]?.jsonObject?.get("deny")?.jsonArray
            ?.map { it.jsonPrimitive.content }
            .orEmpty()

    fun denyStore(rules: JsonObject, name: String): JsonObject {
        val wanted = name.trim()
        if (wanted.isEmpty()) return rules
        val now = deniedStores(rules)
        if (now.any { it.equals(wanted, ignoreCase = true) }) return rules
        return withStores(rules, now + wanted)
    }

    fun allowStore(rules: JsonObject, name: String): JsonObject =
        withStores(rules, deniedStores(rules).filterNot { it.equals(name, ignoreCase = true) })

    private fun withStores(rules: JsonObject, deny: List<String>): JsonObject {
        val stores = rules["stores"]?.jsonObject ?: JsonObject(emptyMap())
        return replace(rules, "stores", replace(stores, "deny", strings(deny)))
    }

    // -- the payout that unlocks the far set ---------------------------------

    fun farOverDollars(rules: JsonObject): Int =
        rules["far"]?.jsonObject?.get("overDollars")?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 30

    fun setFarOverDollars(rules: JsonObject, dollars: Int): JsonObject {
        if (dollars <= 0) return rules
        val far = rules["far"]?.jsonObject ?: JsonObject(emptyMap())
        return replace(rules, "far", replace(far, "overDollars", JsonPrimitive(dollars)))
    }

    // -- the sets of suburbs -------------------------------------------------

    /** Data. One set as the editor writes it, with only what the phone shows. */
    data class Set(val name: String, val suburbs: List<String>, val centre: Centre?)

    data class Centre(val label: String, val latitude: Double, val longitude: Double)

    fun sets(rules: JsonObject): List<Set> =
        rules["profiles"]?.jsonArray?.mapNotNull { entry ->
            val one = entry.jsonObject
            val name = one["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Set(
                name = name,
                suburbs = one["suburbs"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                centre = one["centre"]?.jsonObject?.let { at ->
                    val lat = at["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val lon = at["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    if (lat == null || lon == null) null
                    else Centre(at["label"]?.jsonPrimitive?.content.orEmpty(), lat, lon)
                },
            )
        }.orEmpty()

    fun activeSet(rules: JsonObject): String? = rules["active"]?.jsonPrimitive?.content

    fun addSet(rules: JsonObject, name: String, centre: Centre?, suburbs: List<String>): JsonObject {
        val wanted = name.trim()
        if (wanted.isEmpty() || sets(rules).any { it.name.equals(wanted, ignoreCase = true) }) return rules
        val made = buildJsonObject {
            put("name", JsonPrimitive(wanted))
            put("suburbs", strings(suburbs))
            if (centre != null) put("centre", centreJson(centre))
        }
        val places = rules["profiles"]?.jsonArray.orEmpty()
        return replace(rules, "profiles", JsonArray(places + made))
    }

    fun renameSet(rules: JsonObject, from: String, to: String): JsonObject {
        val wanted = to.trim()
        if (wanted.isEmpty() || wanted == from) return rules
        if (sets(rules).any { it.name.equals(wanted, ignoreCase = true) }) return rules
        val places = rules["profiles"]?.jsonArray?.map { entry ->
            val one = entry.jsonObject
            if (one["name"]?.jsonPrimitive?.content != from) entry
            else replace(one, "name", JsonPrimitive(wanted))
        }.orEmpty()
        val renamed = replace(rules, "profiles", JsonArray(places))
        return if (activeSet(rules) == from) replace(renamed, "active", JsonPrimitive(wanted)) else renamed
    }

    fun copySet(rules: JsonObject, name: String, copyName: String): JsonObject {
        val source = rules["profiles"]?.jsonArray?.firstOrNull {
            it.jsonObject["name"]?.jsonPrimitive?.content == name
        }?.jsonObject ?: return rules
        val wanted = copyName.trim()
        if (wanted.isEmpty() || sets(rules).any { it.name.equals(wanted, ignoreCase = true) }) return rules
        val places = rules["profiles"]?.jsonArray.orEmpty()
        return replace(rules, "profiles", JsonArray(places + replace(source, "name", JsonPrimitive(wanted))))
    }

    fun removeSet(rules: JsonObject, name: String): JsonObject {
        val places = rules["profiles"]?.jsonArray?.filterNot {
            it.jsonObject["name"]?.jsonPrimitive?.content == name
        }.orEmpty()
        val without = replace(rules, "profiles", JsonArray(places))
        // The live set cannot be one that is gone.
        return if (activeSet(rules) != name) without
        else {
            val next = places.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content
            if (next == null) without else replace(without, "active", JsonPrimitive(next))
        }
    }

    fun setCentre(rules: JsonObject, name: String, centre: Centre): JsonObject {
        val places = rules["profiles"]?.jsonArray?.map { entry ->
            val one = entry.jsonObject
            if (one["name"]?.jsonPrimitive?.content != name) entry
            else replace(one, "centre", centreJson(centre))
        }.orEmpty()
        return replace(rules, "profiles", JsonArray(places))
    }

    /** The suburbs of one set, as the map on the phone leaves them. */
    fun setSuburbs(rules: JsonObject, name: String, suburbs: List<String>): JsonObject {
        val places = rules["profiles"]?.jsonArray?.map { entry ->
            val one = entry.jsonObject
            if (one["name"]?.jsonPrimitive?.content != name) entry
            else replace(one, "suburbs", strings(suburbs.distinct().sorted()))
        }.orEmpty()
        return replace(rules, "profiles", JsonArray(places))
    }

    /**
     * One suburb tapped on the map: out of the set if it was in, in if it was
     * not. When the set is the one the laptop has open, its working copy under
     * "suburbs.allow" follows, so the laptop shows the same thing either way.
     */
    fun toggleSuburb(rules: JsonObject, set: String, suburb: String): JsonObject {
        val current = sets(rules).firstOrNull { it.name == set } ?: return rules
        val next = if (suburb in current.suburbs) current.suburbs - suburb else current.suburbs + suburb
        val edited = setSuburbs(rules, set, next)
        if (activeSet(rules) != set) return edited
        val allow = rules["suburbs"]?.jsonObject ?: JsonObject(emptyMap())
        return replace(edited, "suburbs", replace(allow, "allow", strings(next.distinct().sorted())))
    }

    // -- the no-go boxes -----------------------------------------------------

    fun boxes(rules: JsonObject): List<NoGoBox> =
        rules["noGo"]?.jsonArray?.mapNotNull { entry ->
            val one = entry.jsonObject
            fun edge(key: String) = one[key]?.jsonPrimitive?.content?.toDoubleOrNull()
            val south = edge("south") ?: return@mapNotNull null
            val west = edge("west") ?: return@mapNotNull null
            val north = edge("north") ?: return@mapNotNull null
            val east = edge("east") ?: return@mapNotNull null
            NoGoBox(one["label"]?.jsonPrimitive?.content.orEmpty(), south, west, north, east)
        }.orEmpty()

    fun addBox(rules: JsonObject, box: NoGoBox): JsonObject {
        if (box.label.isBlank()) return rules
        val made = buildJsonObject {
            put("label", JsonPrimitive(box.label))
            put("south", JsonPrimitive(box.south))
            put("west", JsonPrimitive(box.west))
            put("north", JsonPrimitive(box.north))
            put("east", JsonPrimitive(box.east))
        }
        return replace(rules, "noGo", JsonArray(rules["noGo"]?.jsonArray.orEmpty() + made))
    }

    fun removeBox(rules: JsonObject, label: String): JsonObject =
        replace(
            rules,
            "noGo",
            JsonArray(
                rules["noGo"]?.jsonArray?.filterNot {
                    it.jsonObject["label"]?.jsonPrimitive?.content == label
                }.orEmpty()
            ),
        )

    // -- the plumbing --------------------------------------------------------

    private fun centreJson(centre: Centre) = buildJsonObject {
        put("label", JsonPrimitive(centre.label))
        put("lat", JsonPrimitive(centre.latitude))
        put("lon", JsonPrimitive(centre.longitude))
    }

    private fun strings(values: List<String>) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    /** The object with one key changed and every other key, known or not, carried through. */
    private fun replace(one: JsonObject, key: String, value: kotlinx.serialization.json.JsonElement): JsonObject =
        JsonObject(one.toMap() + (key to value))
}
