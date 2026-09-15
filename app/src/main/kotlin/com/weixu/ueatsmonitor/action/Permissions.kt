package com.weixu.ueatsmonitor.action

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Action. The two system switches this app cannot turn on by itself. */
object Permissions {

    fun notificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val us = ComponentName(context, OfferListenerService::class.java)
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == us
        }
    }

    fun overlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Since Android 13 the app's own notification - and the two buttons on it -
     * is not shown until this is granted, and losing it is silent.
     */
    fun postNotificationsGranted(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** The screen recorder is the only source that sees an offer card. */
    fun screenReadingGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val us = ComponentName(context, UberScreenService::class.java)
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == us
        }
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openNotificationAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
