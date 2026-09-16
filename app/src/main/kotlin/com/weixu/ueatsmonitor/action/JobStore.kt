package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Job
import com.weixu.ueatsmonitor.domain.JobBoard
import com.weixu.ueatsmonitor.domain.OfferRecord
import com.weixu.ueatsmonitor.domain.Dropoff
import com.weixu.ueatsmonitor.domain.DropoffScreen
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
        val next = JobBoard.add(
            list(context),
            Job(
                atMillis, offer,
                taken = false, address = null, note = null,
                dropAddress = null, dropUnit = null, dropNote = null,
                noteCn = null, dropNoteCn = null,
            ),
        )
        write(context, next)
    }

    fun remove(context: Context, atMillis: Long) {
        write(context, JobBoard.remove(list(context), atMillis))
    }

    fun clear(context: Context) = write(context, emptyList())

    /**
     * Puts a Chinese rendering of each note beside the original. Translation is
     * asynchronous and can take a moment, so the board is read again when the
     * answer arrives - it may have changed in between.
     */
    fun translateNotes(context: Context) {
        list(context).forEach { job ->
            val at = job.atMillis
            if (job.note != null && job.noteCn == null) {
                Notes.inChinese(job.note) { chinese -> amend(context, at) { it.copy(noteCn = chinese) } }
            }
            if (job.dropNote != null && job.dropNoteCn == null) {
                Notes.inChinese(job.dropNote) { chinese -> amend(context, at) { it.copy(dropNoteCn = chinese) } }
            }
        }
    }

    private fun amend(context: Context, atMillis: Long, change: (Job) -> Job) {
        val jobs = list(context)
        val next = jobs.map { if (it.atMillis == atMillis) change(it) else it }
        if (next != jobs) write(context, next)
    }

    /** The delivery screen appeared: give the job the customer's real address. */
    fun markDelivered(context: Context, dropoff: Dropoff) {
        val jobs = list(context)
        val next = JobBoard.delivered(jobs, dropoff, DropoffScreen.suburbOf(dropoff))
        if (next != jobs) write(context, next)
    }

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
                        job.dropAddress?.let { put("dropAddress", JsonPrimitive(it)) }
                        job.dropUnit?.let { put("dropUnit", JsonPrimitive(it)) }
                        job.dropNote?.let { put("dropNote", JsonPrimitive(it)) }
                        job.noteCn?.let { put("noteCn", JsonPrimitive(it)) }
                        job.dropNoteCn?.let { put("dropNoteCn", JsonPrimitive(it)) }
                        put("match", JsonPrimitive(job.offer.isMatch))
                        put("payout", JsonPrimitive(job.offer.payout))
                        put("pickup", JsonPrimitive(job.offer.pickup))
                        put("dropoff", JsonPrimitive(job.offer.dropoff))
                        job.offer.ruling?.let { put("ruling", JsonPrimitive(it)) }
                        job.offer.why?.let { put("why", JsonPrimitive(it)) }
                        put("tree", JsonPrimitive(job.offer.fromTree))
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
            dropAddress = entry["dropAddress"]?.jsonPrimitive?.content,
            dropUnit = entry["dropUnit"]?.jsonPrimitive?.content,
            dropNote = entry["dropNote"]?.jsonPrimitive?.content,
            noteCn = entry["noteCn"]?.jsonPrimitive?.content,
            dropNoteCn = entry["dropNoteCn"]?.jsonPrimitive?.content,
            offer = OfferRecord(
                isMatch = entry["match"]?.jsonPrimitive?.content == "true",
                payout = entry["payout"]!!.jsonPrimitive.content,
                pickup = entry["pickup"]!!.jsonPrimitive.content,
                dropoff = entry["dropoff"]!!.jsonPrimitive.content,
                ruling = entry["ruling"]?.jsonPrimitive?.content,
                why = entry["why"]?.jsonPrimitive?.content,
                fromTree = entry["tree"]?.jsonPrimitive?.content == "true",
            ),
        )
    }.getOrNull()
}
