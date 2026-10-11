package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.CentreChoice
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.TempCentre
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate

/**
 * Action. Today's temporary centre, kept on this phone only.
 *
 * Not in the rules: it is about where this driver is working today, and the
 * laptop's sets should not change because he parked somewhere else for an
 * afternoon. It lapses at midnight on its own - see [CentreChoice].
 */
object TempCentreStore {

    private const val FILE = "temp-centre.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun set(context: Context, label: String, at: GeoPoint) {
        val text = buildJsonObject {
            put("label", label)
            put("lat", at.latitude)
            put("lon", at.longitude)
            put("day", today())
        }.toString()
        runCatching { File(context.filesDir, FILE).writeText(text) }
            .onFailure { Log.w("UEatsMonitor", "temp centre: could not save", it) }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    /** Today's temporary centre, or null. */
    fun today(context: Context): TempCentre? = CentreChoice.stillToday(read(context), today())

    /**
     * The centre everything works around: today's temporary point, else the
     * set's own. Every reader of "the centre" comes through here.
     */
    fun centreInForce(context: Context): GeoPoint? =
        CentreChoice.inForce(read(context), today(), Profiles.centre(context))

    private fun read(context: Context): TempCentre? = runCatching {
        val one = json.parseToJsonElement(File(context.filesDir, FILE).readText()) as JsonObject
        TempCentre(
            label = one["label"]!!.jsonPrimitive.content,
            at = GeoPoint(one["lat"]!!.jsonPrimitive.content.toDouble(), one["lon"]!!.jsonPrimitive.content.toDouble()),
            day = one["day"]!!.jsonPrimitive.content,
        )
    }.getOrNull()

    private fun today(): String = LocalDate.now().toString()
}
