package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Briefing
import com.weixu.ueatsmonitor.domain.Geo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Action. Answers a spoken question about the jobs in hand by asking the
 * `ask` edge function, which holds the Gemini key. What the phone knows is
 * written out by [Briefing] and sent along; the last few turns go too, so
 * "那家店呢" still means something.
 */
object Assistant {

    data class Turn(val question: String, val answer: String)

    /** Kept only in memory: a question about the current job is worthless tomorrow. */
    @Volatile
    private var history: List<Turn> = emptyList()

    sealed interface Reply {
        data class Answer(val words: String, val millis: Long) : Reply
        data class Failed(val why: String) : Reply
    }

    suspend fun ask(context: Context, question: String): Reply = withContext(Dispatchers.IO) {
        val token = Cloud.client.auth.currentAccessTokenOrNull()
            ?: return@withContext Reply.Failed("没登录")
        val body = JsonObject(
            mapOf(
                "question" to JsonPrimitive(question),
                "context" to JsonPrimitive(briefing(context)),
                "history" to JsonArray(
                    history.takeLast(HISTORY).map {
                        JsonObject(mapOf("question" to JsonPrimitive(it.question), "answer" to JsonPrimitive(it.answer)))
                    }
                ),
            )
        )
        runCatching {
            val connection = (URL(Cloud.functionUrl("ask")).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", Cloud.PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
            val reply = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
            reply["answer"]?.jsonPrimitive?.content?.let { words ->
                history = (history + Turn(question, words)).takeLast(HISTORY)
                Reply.Answer(words, reply["millis"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1)
            } ?: Reply.Failed(reply["error"]?.jsonPrimitive?.content ?: "http " + connection.responseCode)
        }.getOrElse { Reply.Failed(it.message ?: it::class.simpleName.orEmpty()) }
            .also { Log.i(TAG, "assistant: q=\"$question\" -> $it") }
    }

    /** The picture of now that goes with every question. */
    fun briefing(context: Context): String {
        val carAt = CurrentPosition(context).lastKnown()?.at
        val suburbs = Gazetteer.suburbs(context)
        val nearest = carAt?.let { car ->
            suburbs.minByOrNull { Geo.straightLine(car, it.at).value }
                ?.takeIf { Geo.straightLine(car, it.at).value * 1.609344 < NEAR_ENOUGH_KM }?.name
        }
        return Briefing.text(
            nowMillis = System.currentTimeMillis(),
            jobs = JobStore.list(context),
            where = Briefing.Whereabouts(carAt = carAt, centre = Profiles.centre(context), nearestSuburb = nearest),
            profileName = Profiles.list(context).firstOrNull { it.active }?.name,
        )
    }

    private const val HISTORY = 3

    /** Further than this from every suburb's middle, and the name would mislead. */
    private const val NEAR_ENOUGH_KM = 4.0

    private const val TAG = "UEatsMonitor"
}
