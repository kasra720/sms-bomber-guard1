package com.smsbomberguard.app

/**
 * AppSettings.kt
 * -----------------------------------------------------------
 * Persistent, user-adjustable settings, backed by DataStore.
 *
 * Defaults (per requirements):
 *   - protectionEnabled = true
 *   - blockDurationMinutes = 10
 *
 * Also exposes an in-memory cached snapshot (`current`) that is kept
 * in sync with DataStore via a Flow collector. SmsReceiver reads
 * `current` synchronously so the detection decision stays fast (no
 * disk I/O on the hot path of an incoming SMS broadcast).
 */

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.distinctUntilChanged

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "bomber_guard_settings")

data class AppSettingsSnapshot(
    val protectionEnabled: Boolean = true,
    val blockDurationMinutes: Int = 10
) {
    val blockDurationMillis: Long get() = blockDurationMinutes * 60_000L
}

object AppSettings {
    private val KEY_ENABLED = booleanPreferencesKey("protection_enabled")
    private val KEY_DURATION_MIN = intPreferencesKey("block_duration_minutes")

    // Fast, synchronous read for the SMS broadcast hot path.
    @Volatile
    var current: AppSettingsSnapshot = AppSettingsSnapshot()
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Call once (e.g. from Application.onCreate) to start syncing DataStore -> memory cache. */
    fun init(context: Context) {
        if (started) return
        started = true
        context.applicationContext.settingsDataStore.data
            .map { prefs ->
                AppSettingsSnapshot(
                    protectionEnabled = prefs[KEY_ENABLED] ?: true,
                    blockDurationMinutes = prefs[KEY_DURATION_MIN] ?: 10
                )
            }
            .distinctUntilChanged()
            .onEach { current = it }
            .launchIn(scope)
    }

    suspend fun setProtectionEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.settingsDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setBlockDurationMinutes(context: Context, minutes: Int) {
        val clamped = minutes.coerceIn(1, 120)
        context.applicationContext.settingsDataStore.edit { it[KEY_DURATION_MIN] = clamped }
    }
}
