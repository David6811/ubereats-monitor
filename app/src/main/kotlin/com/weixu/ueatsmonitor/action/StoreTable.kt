package com.weixu.ueatsmonitor.action

import android.content.Context
import com.weixu.ueatsmonitor.domain.Store
import com.weixu.ueatsmonitor.domain.StoreKinds

/** Action. Reads the bundled shop table off disk once, then never again. */
object StoreTable {

    private const val ASSET = "service-area-stores.csv"

    @Volatile
    private var loaded: List<Store>? = null

    fun all(context: Context): List<Store> = loaded ?: synchronized(this) {
        loaded ?: load(context).also { loaded = it }
    }

    private fun load(context: Context): List<Store> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { StoreKinds.parse(it.readText()) }
    }.getOrDefault(emptyList())
}
