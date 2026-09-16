package com.weixu.ueatsmonitor.action

/**
 * Action. What the reader is doing right now, for the strip along the top of the
 * app.
 *
 * It replaced a dot drawn over every other app: beating that dot twice a second
 * kept the phone's render thread busy for a fifth of its CPU and warmed the
 * phone in its cradle. A word on our own screen costs nothing while the app is
 * not being looked at.
 */
object CaptureStatus {

    enum class State { OFF, WATCHING, IDLE, BROKEN }

    @Volatile
    var state: State = State.OFF
        private set

    @Volatile
    var lastFrameAtMillis: Long = 0
        private set

    fun note(now: Long, state: State) {
        this.state = state
        if (state != State.OFF) lastFrameAtMillis = now
    }
}
