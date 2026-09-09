package com.weixu.ueatsmonitor.action

import android.content.Context
import com.weixu.ueatsmonitor.domain.Suburb
import com.weixu.ueatsmonitor.domain.SuburbIndex

/** Action. Reads the bundled suburb table off disk once, then never again. */
object Gazetteer {

    private const val ASSET = "melbourne-suburbs.csv"

    @Volatile
    private var loaded: List<Suburb>? = null

    fun suburbs(context: Context): List<Suburb> = loaded ?: synchronized(this) {
        loaded ?: load(context).also { loaded = it }
    }

    private fun load(context: Context): List<Suburb> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { SuburbIndex.parse(it.readText()) }
    }.getOrDefault(emptyList())
}
