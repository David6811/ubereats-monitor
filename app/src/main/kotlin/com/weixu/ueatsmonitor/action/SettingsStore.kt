package com.weixu.ueatsmonitor.action

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weixu.ueatsmonitor.domain.Cents
import com.weixu.ueatsmonitor.domain.Miles
import com.weixu.ueatsmonitor.domain.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Action. Reads and writes the driver's bar. The shape it stores is [Settings]. */
class SettingsStore(private val context: Context) {

    data class Settings(
        val thresholds: Thresholds,
        val overlayEnabled: Boolean,
        val pulseEnabled: Boolean,
        /** Whether an offer over the threshold may use the far set at all. */
        val farEnabled: Boolean,
        /** Whether a lane or a highway destination is refused outright. */
        val refuseLanesEnabled: Boolean,
        val vibrateEnabled: Boolean,
        val areaSoundEnabled: Boolean,
        val recordScreenEnabled: Boolean,
        val testModeEnabled: Boolean,
        val logEveryNotification: Boolean,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            thresholds = Thresholds(
                minPayout = Cents(prefs[MIN_PAYOUT_CENTS] ?: Thresholds.STARTER.minPayout.amount),
                minPayPerMile = prefs[MIN_PAY_PER_MILE] ?: Thresholds.STARTER.minPayPerMile,
                minPayPerHour = prefs[MIN_PAY_PER_HOUR] ?: Thresholds.STARTER.minPayPerHour,
                maxDistance = Miles(prefs[MAX_DISTANCE_MILES] ?: Thresholds.STARTER.maxDistance.value),
            ),
            overlayEnabled = prefs[OVERLAY_ENABLED] ?: true,
            pulseEnabled = prefs[PULSE_ENABLED] ?: true,
            farEnabled = prefs[FAR_ENABLED] ?: true,
            refuseLanesEnabled = prefs[REFUSE_LANES] ?: true,
            vibrateEnabled = prefs[VIBRATE_ENABLED] ?: false,
            areaSoundEnabled = prefs[AREA_SOUND_ENABLED] ?: true,
            recordScreenEnabled = prefs[RECORD_SCREEN] ?: false,
            testModeEnabled = prefs[TEST_MODE] ?: false,
            logEveryNotification = prefs[LOG_EVERYTHING] ?: false,
        )
    }

    suspend fun saveThresholds(thresholds: Thresholds) {
        context.dataStore.edit { prefs ->
            prefs[MIN_PAYOUT_CENTS] = thresholds.minPayout.amount
            prefs[MIN_PAY_PER_MILE] = thresholds.minPayPerMile
            prefs[MIN_PAY_PER_HOUR] = thresholds.minPayPerHour
            prefs[MAX_DISTANCE_MILES] = thresholds.maxDistance.value
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) = putBoolean(OVERLAY_ENABLED, enabled)

    suspend fun setPulseEnabled(enabled: Boolean) = putBoolean(PULSE_ENABLED, enabled)

    suspend fun setFarEnabled(enabled: Boolean) = putBoolean(FAR_ENABLED, enabled)

    suspend fun setRefuseLanesEnabled(enabled: Boolean) = putBoolean(REFUSE_LANES, enabled)

    suspend fun setVibrateEnabled(enabled: Boolean) = putBoolean(VIBRATE_ENABLED, enabled)

    suspend fun setAreaSoundEnabled(enabled: Boolean) = putBoolean(AREA_SOUND_ENABLED, enabled)

    suspend fun setTestModeEnabled(enabled: Boolean) = putBoolean(TEST_MODE, enabled)

    suspend fun setRecordScreenEnabled(enabled: Boolean) = putBoolean(RECORD_SCREEN, enabled)

    suspend fun setLogEveryNotification(enabled: Boolean) = putBoolean(LOG_EVERYTHING, enabled)

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    private companion object {
        val MIN_PAYOUT_CENTS = intPreferencesKey("min_payout_cents")
        val MIN_PAY_PER_MILE = doublePreferencesKey("min_pay_per_mile")
        val MIN_PAY_PER_HOUR = doublePreferencesKey("min_pay_per_hour")
        val MAX_DISTANCE_MILES = doublePreferencesKey("max_distance_miles")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val PULSE_ENABLED = booleanPreferencesKey("pulse_enabled")
        val FAR_ENABLED = booleanPreferencesKey("far_enabled")
        val REFUSE_LANES = booleanPreferencesKey("refuse_lanes")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val AREA_SOUND_ENABLED = booleanPreferencesKey("area_sound_enabled")
        val RECORD_SCREEN = booleanPreferencesKey("record_screen")
        val TEST_MODE = booleanPreferencesKey("test_mode")
        val LOG_EVERYTHING = booleanPreferencesKey("log_everything")
    }
}
