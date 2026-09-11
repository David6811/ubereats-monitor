package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Job
import com.weixu.ueatsmonitor.domain.JobBoard
import com.weixu.ueatsmonitor.domain.OfferRecord
import com.weixu.ueatsmonitor.domain.Pickup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/**
 * Action. The board of jobs in hand, on disk.
 *
 * On disk rather than in memory because the two ends are different processes'
 * worth of lifetime: the screen reader adds to it whenever a card appears, and
 * the screen may be opened hours later, after the activity has long been killed.
 */
object JobStore {

    private const val FILE_NAME = "jobs.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun list(context: Context): List<Job> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching {
            json.parseToJsonElement(file.readText()).jsonArray.mapNotNull(::jobOf)
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, atMillis: Long, offer: OfferRecord) {
        val next = JobBoard.add(list(context), Job(atMillis, offer, taken = false, address = null, note = null))
        write(context, next)
    }

    fun remove(context: Context, atMillis: Long) {
        write(context, JobBoard.remove(list(context), atMillis))
    }

    fun clear(context: Context) = write(context, emptyList())

    /** The pickup screen appeared: mark the job it belongs to, if it is still here. */
    fun markTaken(context: Context, pickup: Pickup) {
        val jobs = list(context)
        val next = JobBoard.taken(jobs, pickup)
        if (next != jobs) write(context, next)
    }

    private fun write(context: Context, jobs: List<Job>) {
        val array: JsonArray = buildJsonArray {
            jobs.forEach { job ->
                add(
                    buildJsonObject {
                        put("at", JsonPrimitive(job.atMillis))
                        put("taken", JsonPrimitive(job.taken))
                        job.address?.let { put("address", JsonPrimitive(it)) }
                        job.note?.let { put("note", JsonPrimitive(it)) }
                        put("match", JsonPrimitive(job.offer.isMatch))
                        put("payout", JsonPrimitive(job.offer.payout))
                        put("pickup", JsonPrimitive(job.offer.pickup))
                        put("dropoff", JsonPrimitive(job.offer.dropoff))
                        job.offer.ruling?.let { put("ruling", JsonPrimitive(it)) }
                        job.offer.why?.let { put("why", JsonPrimitive(it)) }
                    }
                )
            }
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(array.toString()) }
            .onFailure { Log.w("UEatsMonitor", "jobs: could not write the board", it) }
    }

    private fun jobOf(element: kotlinx.serialization.json.JsonElement): Job? = runCatching {
        val entry = element.jsonObject
        Job(
            atMillis = entry["at"]!!.jsonPrimitive.long,
            taken = entry["taken"]?.jsonPrimitive?.content == "true",
            address = entry["address"]?.jsonPrimitive?.content,
            note = entry["note"]?.jsonPrimitive?.content,
            offer = OfferRecord(
                isMatch = entry["match"]?.jsonPrimitive?.content == "true",
                payout = entry["payout"]!!.jsonPrimitive.content,
                pickup = entry["pickup"]!!.jsonPrimitive.content,
                dropoff = entry["dropoff"]!!.jsonPrimitive.content,
                ruling = entry["ruling"]?.jsonPrimitive?.content,
                why = entry["why"]?.jsonPrimitive?.content,
            ),
        )
    }.getOrNull()
}
