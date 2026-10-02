package com.weixu.ueatsmonitor.action

import com.weixu.ueatsmonitor.domain.Lang
import com.weixu.ueatsmonitor.domain.Words
import com.weixu.ueatsmonitor.domain.wordsIn
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Action. The listener service runs on a binder thread and cannot suspend, so the
 * newest settings are mirrored here by [com.weixu.ueatsmonitor.App].
 */
object LiveSettings {
    @Volatile
    var current: SettingsStore.Settings? = null
}

/**
 * The language the driver picked, for the parts of the app that are not a
 * screen - the overlay, the notifications, what is said aloud. Chinese until
 * the settings have been read once, which is what the app started as.
 */
fun driverLang(): Lang = LiveSettings.current?.lang ?: Lang.CHINESE

fun driverWords(): Words = wordsIn(driverLang())

/** Action. Whether Android has actually bound our notification listener. */
object ListenerStatus {
    val connected = MutableStateFlow(false)
}
