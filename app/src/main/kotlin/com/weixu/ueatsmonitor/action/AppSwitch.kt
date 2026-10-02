package com.weixu.ueatsmonitor.action

import android.content.Context
import android.content.Intent
import android.util.Log
import com.weixu.ueatsmonitor.domain.VoiceTarget

/**
 * Action. Brings another app to the front.
 *
 * Spoken commands and the floating buttons both do this, and neither should
 * carry its own copy of the flags: REORDER_TO_FRONT is what returns Uber to the
 * screen it was left on rather than to its home page.
 */
object AppSwitch {

    /** False when the app is not installed; the caller says so in its own words. */
    fun bringForward(context: Context, target: VoiceTarget): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(target.packageName) ?: return false
        runCatching {
            context.startActivity(
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }.onFailure { Log.w(TAG, "switch to " + target.packageName + " failed", it) }
        return true
    }

    private const val TAG = "UEatsMonitor"
}
