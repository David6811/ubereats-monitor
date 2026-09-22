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
import com.weixu.ueatsmonitor.domain.Lang
import com.weixu.ueatsmonitor.domain.Miles
import com.weixu.ueatsmonitor.domain.Thresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Action. Reads and writes the driver's bar. The shape it stores is [Settings]. */
class SettingsStore(private val context: Context) {

    data class Settings(
        val thresholds: Thresholds,
        /** Whether an offer over the threshold may use the far set at all. */
        val farEnabled: Boolean,
        val vibrateEnabled: Boolean,
        val areaSoundEnabled: Boolean,
        val recordScreenEnabled: Boolean,
        val logEveryNotification: Boolean,
        /** Whether the microphone stays open for spoken commands. */
        val voiceEnabled: Boolean,
        /**
         * The email and password last signed in with. Kept so the driver never
         * types them at the wheel: this is his own phone, and app storage is
         * his alone. Cleared on sign-out.
         */
        val lastEmail: String,
        val lastPassword: String,
        /** Dollars of petrol per kilometre, driven out and back. */
        val fuelPerKm: Double,
        /** The card's minutes times this is the trip there and back. */
        val timeFactor: Double,
        /** A far-set offer under this many dollars an hour, after petrol, is left. */
        val farMinPerHour: Double,
        /** Only take what leaves him nearer the centre of the set he is working. */
        val homewardEnabled: Boolean,
        /** On the homeward rule, a drop this close to the centre is taken even if it leads away. */
        val homewardNearKm: Double,
        /** On the homeward rule, a job longer than this is left. */
        val homewardMaxMinutes: Int,
        /** Stay around the middle: only drops within a few km, on short jobs. */
        val nearCentreEnabled: Boolean,
        val nearCentreMaxKm: Double,
        val nearCentreMaxMinutes: Int,
        /** The floating 关导航 / 语音 buttons over other apps during a shift. */
        val toolsEnabled: Boolean,
        /** The language every word the driver reads is written in. */
        val lang: Lang,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            thresholds = Thresholds(
                minPayout = Cents(prefs[MIN_PAYOUT_CENTS] ?: Thresholds.STARTER.minPayout.amount),
                minPayPerMile = prefs[MIN_PAY_PER_MILE] ?: Thresholds.STARTER.minPayPerMile,
                minPayPerHour = prefs[MIN_PAY_PER_HOUR] ?: Thresholds.STARTER.minPayPerHour,
                maxDistance = Miles(prefs[MAX_DISTANCE_MILES] ?: Thresholds.STARTER.maxDistance.value),
            ),
            farEnabled = prefs[FAR_ENABLED] ?: true,
            vibrateEnabled = prefs[VIBRATE_ENABLED] ?: false,
            areaSoundEnabled = prefs[AREA_SOUND_ENABLED] ?: true,
            recordScreenEnabled = prefs[RECORD_SCREEN] ?: false,
            logEveryNotification = prefs[LOG_EVERYTHING] ?: false,
            voiceEnabled = prefs[VOICE_ENABLED] ?: false,
            lastEmail = prefs[LAST_EMAIL] ?: "",
            lastPassword = prefs[LAST_PASSWORD] ?: "",
            fuelPerKm = prefs[FUEL_PER_KM] ?: DEFAULT_FUEL_PER_KM,
            timeFactor = prefs[TIME_FACTOR] ?: DEFAULT_TIME_FACTOR,
            farMinPerHour = prefs[FAR_MIN_PER_HOUR] ?: DEFAULT_FAR_MIN_PER_HOUR,
            homewardEnabled = prefs[HOMEWARD] ?: false,
            homewardNearKm = prefs[HOMEWARD_NEAR_KM] ?: DEFAULT_HOMEWARD_NEAR_KM,
            homewardMaxMinutes = prefs[HOMEWARD_MAX_MINUTES] ?: DEFAULT_HOMEWARD_MAX_MINUTES,
            nearCentreEnabled = prefs[NEAR_CENTRE] ?: false,
            nearCentreMaxKm = prefs[NEAR_CENTRE_MAX_KM] ?: DEFAULT_NEAR_CENTRE_MAX_KM,
            nearCentreMaxMinutes = prefs[NEAR_CENTRE_MAX_MINUTES] ?: DEFAULT_NEAR_CENTRE_MAX_MINUTES,
            toolsEnabled = prefs[TOOLS] ?: true,
            lang = if ((prefs[LANG] ?: defaultLang()) == "en") Lang.ENGLISH else Lang.CHINESE,
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

    suspend fun setFarEnabled(enabled: Boolean) = putBoolean(FAR_ENABLED, enabled)

    suspend fun setVibrateEnabled(enabled: Boolean) = putBoolean(VIBRATE_ENABLED, enabled)

    suspend fun setAreaSoundEnabled(enabled: Boolean) = putBoolean(AREA_SOUND_ENABLED, enabled)

    suspend fun setRecordScreenEnabled(enabled: Boolean) = putBoolean(RECORD_SCREEN, enabled)

    suspend fun setLogEveryNotification(enabled: Boolean) = putBoolean(LOG_EVERYTHING, enabled)

    suspend fun setVoiceEnabled(enabled: Boolean) = putBoolean(VOICE_ENABLED, enabled)

    suspend fun rememberLogin(email: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_EMAIL] = email
            prefs[LAST_PASSWORD] = password
        }
    }

    suspend fun forgetLogin() {
        context.dataStore.edit { prefs -> prefs.remove(LAST_PASSWORD) }
    }

    suspend fun setHomewardEnabled(enabled: Boolean) = putBoolean(HOMEWARD, enabled)

    suspend fun setNearCentreEnabled(enabled: Boolean) = putBoolean(NEAR_CENTRE, enabled)

    suspend fun setToolsEnabled(enabled: Boolean) = putBoolean(TOOLS, enabled)

    suspend fun setLang(lang: Lang) {
        context.dataStore.edit { prefs -> prefs[LANG] = if (lang == Lang.ENGLISH) "en" else "zh" }
    }

    /** Before the driver has chosen, the phone's own language decides. */
    private fun defaultLang(): String =
        if (java.util.Locale.getDefault().language == "zh") "zh" else "en"

    suspend fun saveNearCentreLimits(maxKm: Double, maxMinutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[NEAR_CENTRE_MAX_KM] = maxKm
            prefs[NEAR_CENTRE_MAX_MINUTES] = maxMinutes
        }
    }

    suspend fun saveHomewardLimits(nearKm: Double, maxMinutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[HOMEWARD_NEAR_KM] = nearKm
            prefs[HOMEWARD_MAX_MINUTES] = maxMinutes
        }
    }

    suspend fun saveTripCost(fuelPerKm: Double, timeFactor: Double, farMinPerHour: Double) {
        context.dataStore.edit { prefs ->
            prefs[FUEL_PER_KM] = fuelPerKm
            prefs[TIME_FACTOR] = timeFactor
            prefs[FAR_MIN_PER_HOUR] = farMinPerHour
        }
    }

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    companion object {
        private val MIN_PAYOUT_CENTS = intPreferencesKey("min_payout_cents")
        private val MIN_PAY_PER_MILE = doublePreferencesKey("min_pay_per_mile")
        private val MIN_PAY_PER_HOUR = doublePreferencesKey("min_pay_per_hour")
        private val MAX_DISTANCE_MILES = doublePreferencesKey("max_distance_miles")
        private val FAR_ENABLED = booleanPreferencesKey("far_enabled")
        private val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        private val AREA_SOUND_ENABLED = booleanPreferencesKey("area_sound_enabled")
        private val RECORD_SCREEN = booleanPreferencesKey("record_screen")
        private val LOG_EVERYTHING = booleanPreferencesKey("log_everything")
        private val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        private val LAST_EMAIL = androidx.datastore.preferences.core.stringPreferencesKey("last_email")
        private val LAST_PASSWORD = androidx.datastore.preferences.core.stringPreferencesKey("last_password")
        private val HOMEWARD = booleanPreferencesKey("homeward")
        private val HOMEWARD_NEAR_KM = doublePreferencesKey("homeward_near_km")
        private val HOMEWARD_MAX_MINUTES = intPreferencesKey("homeward_max_minutes")
        private val NEAR_CENTRE = booleanPreferencesKey("near_centre")
        private val TOOLS = booleanPreferencesKey("tools")
        private val LANG = androidx.datastore.preferences.core.stringPreferencesKey("lang")
        private val NEAR_CENTRE_MAX_KM = doublePreferencesKey("near_centre_max_km")
        private val NEAR_CENTRE_MAX_MINUTES = intPreferencesKey("near_centre_max_minutes")
        private val FUEL_PER_KM = doublePreferencesKey("fuel_per_km")
        private val TIME_FACTOR = doublePreferencesKey("time_factor")
        private val FAR_MIN_PER_HOUR = doublePreferencesKey("far_min_per_hour")

        // The driver's own reckoning: a 2010 Camry in stop-start town driving,
        // and the way back taking half as long again as the way there.
        const val DEFAULT_FUEL_PER_KM = 0.2
        const val DEFAULT_TIME_FACTOR = 1.5
        const val DEFAULT_FAR_MIN_PER_HOUR = 10.0
        const val DEFAULT_HOMEWARD_NEAR_KM = 4.0
        const val DEFAULT_HOMEWARD_MAX_MINUTES = 20
        const val DEFAULT_NEAR_CENTRE_MAX_KM = 4.0
        const val DEFAULT_NEAR_CENTRE_MAX_MINUTES = 30
    }
}
