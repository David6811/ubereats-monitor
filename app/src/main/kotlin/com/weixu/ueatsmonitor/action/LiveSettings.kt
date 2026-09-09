package com.weixu.ueatsmonitor.action

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Action. The listener service runs on a binder thread and cannot suspend, so the
 * newest settings are mirrored here by [com.weixu.ueatsmonitor.App].
 */
object LiveSettings {
    @Volatile
    var current: SettingsStore.Settings? = null
}

/** Action. Whether Android has actually bound our notification listener. */
object ListenerStatus {
    val connected = MutableStateFlow(false)
}
