package com.weixu.ueatsmonitor.action

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Action. Mirrors the phone's job board to the driver's rows in the cloud, so
 * the laptop can show the same board.
 *
 * One way only, phone to cloud: the phone is where the board is made. Every
 * write of the board queues a push; pushes are coalesced, so a burst of
 * frames touching the same job costs one request. A push that fails is
 * simply retried by the next write - the file on the phone is the truth.
 */
object JobsSync {

    @Serializable
    private data class Row(val user_id: String, val at: Long, val job: JsonObject)

    @Serializable
    private data class AtOnly(val at: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pending: CoroutineJob? = null

    @Volatile
    private var latest: JsonArray? = null

    /** The board as the file holds it, to go up after a short pause. */
    fun push(board: JsonArray) {
        latest = board
        if (pending?.isActive == true) return
        pending = scope.launch {
            delay(SETTLE_MILLIS)
            val userId = Cloud.userId() ?: return@launch
            val snapshot = latest ?: return@launch
            runCatching {
                val rows = snapshot.map { entry ->
                    val job = entry.jsonObject
                    Row(userId, job["at"]!!.jsonPrimitive.long, job)
                }
                if (rows.isNotEmpty()) Cloud.client.from(TABLE).upsert(rows)
                // Cleared on the phone means gone here too.
                val keep = rows.map { it.at }.toSet()
                val there = Cloud.client.from(TABLE).select(columns = io.github.jan.supabase.postgrest.query.Columns.list("at")) {
                    filter { eq("user_id", userId) }
                }.decodeList<AtOnly>().map { it.at }
                there.filterNot { it in keep }.forEach { gone ->
                    Cloud.client.from(TABLE).delete { filter { eq("user_id", userId); eq("at", gone) } }
                }
                Log.i(TAG, "jobs sync: " + rows.size + " up")
            }.onFailure { Log.w(TAG, "jobs sync: failed, next write retries", it) }
        }
    }

    private const val TABLE = "jobs"

    /** Frames arrive every two seconds; a change settles in less. */
    private const val SETTLE_MILLIS = 1_500L

    private const val TAG = "UEatsMonitor"
}
