package com.weixu.ueatsmonitor.action

import kotlinx.coroutines.flow.MutableStateFlow

/** Action. Whether the screen recorder is currently running. */
object RecordingStatus {
    val running = MutableStateFlow(false)
}
