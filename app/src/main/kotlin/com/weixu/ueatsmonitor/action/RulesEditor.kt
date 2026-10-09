package com.weixu.ueatsmonitor.action

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Action. Changes the rules from the phone and sends them up.
 *
 * Until now the phone only read them: everything that shapes a shift - which
 * suburbs, which shops, the boxes, the money - was drawn on a laptop. A driver
 * who has to open a laptop to use this does not use it, so the phone has to be
 * able to do all of it alone.
 *
 * The rules are one JSON object. Every change is a function from that object
 * to the next one, applied here, written to the same file the judge reads, and
 * pushed to the cloud. The file is the truth either way: a push that fails
 * leaves the phone right and the laptop behind, which the next pull settles.
 */
object RulesEditor {

    @Serializable
    private data class Row(val user_id: String, val rules: JsonObject)

    @Serializable
    private data class Stamped(val rules: JsonObject, val updated_at: String)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    sealed interface Saved {
        data object Pushed : Saved

        /** Written on the phone; the cloud will take it on the next try. */
        data class OnPhoneOnly(val why: String) : Saved

        /** The laptop saved after this phone last looked. Nothing was written. */
        data object CloudMovedOn : Saved
    }

    /**
     * Reads the rules, hands them to [change], and keeps what comes back.
     *
     * [change] is a calculation: same rules in, same rules out, no IO. Return
     * the object unchanged to save nothing.
     */
    suspend fun edit(context: Context, change: (JsonObject) -> JsonObject): Saved {
        val file = File(context.getExternalFilesDir(null), FILE_NAME)
        val before = read(file)
        val after = change(before)
        if (after == before) return Saved.Pushed

        // Refused rather than merged when the laptop has saved since this phone
        // last pulled. Merging two whole rule sets is guesswork, and the loser
        // is a set of suburbs somebody drew.
        val userId = Cloud.userId() ?: run {
            write(file, after)
            return Saved.OnPhoneOnly("not signed in")
        }
        val cloud = runCatching {
            Cloud.client.from(TABLE).select { filter { eq("user_id", userId) } }.decodeSingleOrNull<Stamped>()
        }.getOrNull()
        if (cloud != null && cloud.rules != before) {
            Log.w(TAG, "rules edit: the cloud moved on, nothing written")
            return Saved.CloudMovedOn
        }

        write(file, after)
        return runCatching {
            Cloud.client.from(TABLE).upsert(Row(userId, after))
            Log.i(TAG, "rules edit: saved and pushed")
            Saved.Pushed as Saved
        }.getOrElse {
            Log.w(TAG, "rules edit: saved on the phone, push failed", it)
            Saved.OnPhoneOnly(it.message ?: it::class.simpleName.orEmpty())
        }
    }

    /** The rules as they stand, or an empty object when there are none yet. */
    fun read(context: Context): JsonObject = read(File(context.getExternalFilesDir(null), FILE_NAME))

    private fun read(file: File): JsonObject = runCatching {
        json.parseToJsonElement(file.readText()) as JsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    /**
     * Written beside and renamed, never in place: a half-written rules.json is
     * read by the judge as no rules at all, and the judge reads it every frame.
     */
    private fun write(file: File, rules: JsonObject) {
        runCatching {
            val next = File(file.parentFile, "$FILE_NAME.new")
            next.writeText(json.encodeToString(JsonObject.serializer(), rules))
            if (!next.renameTo(file)) {
                file.writeText(next.readText())
                next.delete()
            }
        }.onFailure { Log.w(TAG, "rules edit: could not write", it) }
    }

    private const val FILE_NAME = "rules.json"
    private const val TABLE = "rules"
    private const val TAG = "UEatsMonitor"
}
