package com.weixu.ueatsmonitor.action

import android.app.Notification
import android.app.PendingIntent
import android.util.Log

/**
 * Action. The button Google Maps itself puts on its navigation notification.
 *
 * Pressing the cross on the map needs the map on screen, and while Maps is a
 * picture in picture it is not a window the reader can see at all - the
 * accessibility service lists only the shade and Uber. Its notification does
 * not care what is on screen, and the one action it carries while a route is
 * running is the one that ends it.
 *
 * Held from the listener; gone the moment the notification is.
 */
object MapsNotice {

    /** Words a close action goes by, in whatever language the phone is in. */
    private val CLOSING = listOf(
        "exit", "close", "stop", "end", "cancel",
        "退出", "结束", "停止", "关闭", "取消",
    )

    @Volatile
    private var exit: PendingIntent? = null

    /** True while Maps says a route is running. */
    val navigating: Boolean get() = exit != null

    fun saw(notification: Notification) {
        val action = notification.actions.orEmpty().firstOrNull { one ->
            val title = one.title?.toString().orEmpty()
            Log.i(TAG, "maps notice: action \"" + title + "\"")
            CLOSING.any { title.contains(it, ignoreCase = true) }
        }
        if (action?.actionIntent != null) exit = action.actionIntent
    }

    fun gone() {
        exit = null
    }

    /** Fires it. False when Maps is not navigating, or the button has expired. */
    fun stopNavigating(): Boolean {
        val send = exit ?: return false
        return runCatching { send.send(); true }
            .onFailure { Log.w(TAG, "maps notice: its own button would not fire", it) }
            .getOrDefault(false)
    }

    private const val TAG = "UEatsMonitor"
}
