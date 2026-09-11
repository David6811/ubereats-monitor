package com.weixu.ueatsmonitor.action

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Action. Turns a shop's or a customer's note into Chinese, on the phone.
 *
 * The model is about thirty megabytes and is fetched once, over an unmetered
 * connection; after that nothing leaves the phone and nothing is paid for. Until
 * it arrives, notes are shown as they came, which is what they are for.
 */
object Notes {

    private const val TAG = "UEatsMonitor"

    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build()
        )
    }

    @Volatile
    private var ready = false

    @Volatile
    private var asking = false

    /**
     * The note in Chinese, handed to [then] when there is one. Never blocks: the
     * first call starts the download and returns nothing, and the note it was
     * called for is translated the next time that screen is read.
     */
    fun inChinese(note: String, then: (String) -> Unit) {
        if (note.isBlank()) return
        if (!ready) {
            fetchModel()
            return
        }
        translator.translate(note)
            .addOnSuccessListener { translated ->
                if (translated.isNotBlank() && translated != note) then(translated)
            }
            .addOnFailureListener { Log.w(TAG, "notes: could not translate", it) }
    }

    private fun fetchModel() {
        if (asking) return
        asking = true
        // Wi-Fi only. Thirty megabytes is not something to spend a shift's data on.
        translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
            .addOnSuccessListener {
                ready = true
                Log.i(TAG, "notes: translation model ready")
            }
            .addOnFailureListener { error ->
                asking = false
                Log.i(TAG, "notes: no translation model yet (" + error.message + ")")
            }
    }
}
