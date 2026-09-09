package com.weixu.ueatsmonitor.action

import android.content.Context
import com.weixu.ueatsmonitor.domain.ParseResult
import com.weixu.ueatsmonitor.domain.RawNotification
import com.weixu.ueatsmonitor.domain.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Data. One notification as it was seen, plus what we made of it. */
@Serializable
data class LoggedEvent(
    val raw: RawNotification,
    val result: ParseResult,
    val verdict: Verdict?,
)

/**
 * Action. Keeps the last [CAPACITY] notifications in memory and mirrors them to a file,
 * so the regexes in OfferParser can be tuned against what Uber really sends.
 */
object OfferLog {

    private const val CAPACITY = 100
    private const val FILE_NAME = "offer-log.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val EVENTS = ListSerializer(LoggedEvent.serializer())
    private val state = MutableStateFlow<List<LoggedEvent>>(emptyList())
    private var file: File? = null

    val events: StateFlow<List<LoggedEvent>> = state.asStateFlow()

    fun load(context: Context) {
        val target = File(context.filesDir, FILE_NAME).also { file = it }
        if (!target.exists()) return
        state.value = runCatching {
            json.decodeFromString(EVENTS, target.readText())
        }.getOrDefault(emptyList())
    }

    fun add(event: LoggedEvent) {
        state.value = (listOf(event) + state.value).take(CAPACITY)
        persist()
    }

    fun clear() {
        state.value = emptyList()
        persist()
    }

    private fun persist() {
        val target = file ?: return
        runCatching { target.writeText(json.encodeToString(EVENTS, state.value)) }
    }
}
