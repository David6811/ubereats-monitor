package com.weixu.ueatsmonitor

import android.app.Application
import com.weixu.ueatsmonitor.action.LiveSettings
import com.weixu.ueatsmonitor.action.OfferLog
import com.weixu.ueatsmonitor.action.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Action. Wires the two long-lived pieces of state before any screen or service runs. */
class App : Application() {

    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsStore = SettingsStore(this)
        OfferLog.load(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            settingsStore.settings.collect { LiveSettings.current = it }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
