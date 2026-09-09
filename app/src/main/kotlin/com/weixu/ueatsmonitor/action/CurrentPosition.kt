package com.weixu.ueatsmonitor.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.weixu.ueatsmonitor.domain.GeoPoint
import com.weixu.ueatsmonitor.domain.PositionFix

/**
 * Action. The phone's most recent fix, or null. It never asks for a new fix -
 * reviewing captures after a shift does not need the GPS to spin up.
 */
class CurrentPosition(private val context: Context) {

    fun lastKnown(): PositionFix? {
        if (!granted()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return PROVIDERS
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { PositionFix(GeoPoint(it.latitude, it.longitude), it.time) }
    }

    private fun granted(): Boolean = PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
