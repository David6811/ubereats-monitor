package com.weixu.ueatsmonitor.action

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Action. Ends a Google Maps navigation the way a thumb would: press the cross.
 *
 * The cross can only be pressed while Maps is on screen, and while driving it
 * is usually behind Uber. So Maps is brought forward first, given a moment to
 * draw, then the cross is pressed; one more try if it was slow. Shared by the
 * spoken command and the floating button.
 */
object MapsNavigation {

    const val PACKAGE = "com.google.android.apps.maps"

    private val main = Handler(Looper.getMainLooper())

    /** Calls [done] once, on the main thread, with whether the cross was pressed. */
    fun stop(context: Context, done: (Boolean) -> Unit) {
        if (UberScreenService.stopMapsNavigation()) {
            main.post { done(true) }
            return
        }
        bringForward(context)
        main.postDelayed({
            if (UberScreenService.stopMapsNavigation()) return@postDelayed done(true)
            main.postDelayed({ done(UberScreenService.stopMapsNavigation()) }, BRING_FORWARD_MILLIS)
        }, BRING_FORWARD_MILLIS)
    }

    private fun bringForward(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        runCatching {
            context.startActivity(
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }.onFailure { Log.w(TAG, "maps: bring forward failed", it) }
    }

    /** How long Maps takes to be on screen and readable after being asked forward. */
    private const val BRING_FORWARD_MILLIS = 1_500L

    private const val TAG = "UEatsMonitor"
}
