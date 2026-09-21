package com.weixu.ueatsmonitor

import android.app.Application
import com.weixu.ueatsmonitor.action.Cloud
import com.weixu.ueatsmonitor.action.LiveSettings
import com.weixu.ueatsmonitor.action.RulesSync
import io.github.jan.supabase.auth.status.SessionStatus
import com.weixu.ueatsmonitor.action.OfferLog
import com.weixu.ueatsmonitor.action.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
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
        val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        background.launch {
            settingsStore.settings.collect { LiveSettings.current = it }
        }
        // Every sign-in, including the saved session on start-up, brings the
        // cloud's rules down to the file the judge reads.
        background.launch {
            var watching: Job? = null
            Cloud.session.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    RulesSync.pull(this@App)
                    if (watching?.isActive != true) {
                        watching = launch { RulesSync.watch(this@App, this) ; awaitCancellation() }
                    }
                } else {
                    watching?.cancel()
                    watching = null
                }
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
