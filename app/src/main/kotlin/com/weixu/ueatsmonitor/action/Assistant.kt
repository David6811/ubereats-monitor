package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import com.weixu.ueatsmonitor.domain.Briefing
import com.weixu.ueatsmonitor.domain.Geo
import com.weixu.ueatsmonitor.domain.Lang
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
        val token = accessToken() ?: return@withContext Reply.Failed(driverWords().notSignedIn).also {
            Log.w(TAG, "assistant: no access token, session status " + Cloud.session.value)
        }
        val body = JsonObject(
            mapOf(
                "question" to JsonPrimitive(question),
                "context" to JsonPrimitive(briefing(context)),
                "lang" to JsonPrimitive(if (lang() == Lang.ENGLISH) "en" else "zh"),
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

    /**
     * The signed-in driver's token. The saved session is loaded when the app
     * process starts, but a process that Android started for the screen reader
     * alone may not have finished that, and a token that expired while the
     * phone was offline is only refreshed on the next tick: so wait a moment
     * for a load, and ask for a refresh when the token is gone.
     */
    private suspend fun accessToken(): String? {
        val status = withTimeoutOrNull(SESSION_WAIT_MILLIS) {
            Cloud.session.first { it !is SessionStatus.Initializing }
        } ?: Cloud.session.value
        return when (status) {
            is SessionStatus.Authenticated -> status.session.accessToken
            is SessionStatus.RefreshFailure -> {
                runCatching { Cloud.client.auth.refreshCurrentSession() }
                    .onFailure { Log.w(TAG, "assistant: refresh failed", it) }
                Cloud.client.auth.currentAccessTokenOrNull()
            }
            else -> Cloud.client.auth.currentAccessTokenOrNull()
        }
    }

    /** A fresh process loads the saved session in well under this. */
    private const val SESSION_WAIT_MILLIS = 10_000L

    /** The language the driver reads and listens in, as the settings have it. */
    private fun lang(): Lang = LiveSettings.current?.lang ?: Lang.CHINESE

    /** The picture of now that goes with every question. */
    fun briefing(context: Context): String {
        val carAt = CurrentPosition(context).lastKnown()?.at
        val suburbs = Gazetteer.suburbs(context)
        val nearest = carAt?.let { car ->
            suburbs.minByOrNull { Geo.straightLine(car, it.at).value }
                ?.takeIf { Geo.straightLine(car, it.at).value * 1.609344 < NEAR_ENOUGH_KM }?.name
        }
        return Briefing.text(
            lang = lang(),
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
