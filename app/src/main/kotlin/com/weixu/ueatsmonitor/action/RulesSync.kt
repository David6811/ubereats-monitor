package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Action. Keeps the phone's `rules.json` equal to the signed-in driver's row
 * in the cloud.
 *
 * Everything that judges a card reads that file and watches its modified
 * time, so the cloud is brought down to the file rather than read directly:
 * the judge keeps working with no network, and nothing else had to change.
 *
 * The first time a driver signs in with rules already on the phone and none
 * in the cloud, the phone's copy goes up - so nothing drawn on the laptop
 * before this existed is lost.
 */
object RulesSync {

    @Serializable
    private data class Row(val user_id: String, val rules: JsonObject)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** What the last pull did, for the journal and the settings page. */
    sealed interface Outcome {
        data object Updated : Outcome
        data object Unchanged : Outcome
        data object SeededFromPhone : Outcome
        data object NothingAnywhere : Outcome
        data class Failed(val why: String) : Outcome
    }

    suspend fun pull(context: Context): Outcome {
        val userId = Cloud.userId() ?: return Outcome.Failed("not signed in")
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        return runCatching {
            val row = Cloud.client.from(TABLE)
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<Row>()
            val local = runCatching { json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull()
            when {
                row == null && local == null -> Outcome.NothingAnywhere
                row == null -> {
                    Cloud.client.from(TABLE).upsert(Row(userId, local!!))
                    Outcome.SeededFromPhone
                }
                row.rules == local -> Outcome.Unchanged
                else -> {
                    replace(file, json.encodeToString(JsonObject.serializer(), row.rules))
                    Outcome.Updated
                }
            }
        }.getOrElse { Outcome.Failed(it.message ?: it::class.simpleName.orEmpty()) }
            .also { outcome ->
                Log.i(TAG, "rules sync: $outcome")
                when (outcome) {
                    Outcome.Updated -> ServiceJournal.note(context, "规则已从云端更新")
                    Outcome.SeededFromPhone -> ServiceJournal.note(context, "手机上的规则已上传到云端")
                    is Outcome.Failed -> ServiceJournal.note(context, "规则同步失败：" + outcome.why)
                    else -> Unit
                }
            }
    }

    /**
     * A file `adb push` put there belongs to the shell, and this app may not
     * write into it - only replace it. So the new text goes to a sibling and is
     * renamed over the old one, which also means a reader never sees half a file.
     */
    private fun replace(file: File, text: String) {
        val fresh = File(file.parentFile, file.name + ".new")
        fresh.writeText(text)
        if (!fresh.renameTo(file)) {
            file.delete()
            check(fresh.renameTo(file)) { "could not replace " + file.name }
        }
    }

    private const val TABLE = "rules"
    private const val FILE_NAME = "rules.json"
    private const val TAG = "UEatsMonitor"
}
