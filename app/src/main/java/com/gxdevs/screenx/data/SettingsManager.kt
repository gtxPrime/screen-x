package com.gxdevs.screenx.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screenx_settings")

class SettingsManager(private val context: Context) {
    companion object {
        val KEY_FPS = intPreferencesKey("fps")
        val KEY_RESOLUTION = stringPreferencesKey("resolution")
        val KEY_BITRATE = intPreferencesKey("bitrate") // in bps, e.g. 8000000 for 8Mbps
        val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
        val KEY_COUNTDOWN = intPreferencesKey("countdown")
        val KEY_SHOW_FLOATING = booleanPreferencesKey("show_floating")
        val KEY_HIDE_DURING_RECORD = booleanPreferencesKey("hide_during_record")
        val KEY_SAVE_LOCATION = stringPreferencesKey("save_location")
        val KEY_ADAPTIVE_THEME = booleanPreferencesKey("adaptive_theme")
        val KEY_SHAKE_TO_STOP = booleanPreferencesKey("shake_to_stop")
        val KEY_ORIENTATION = stringPreferencesKey("orientation")
        val KEY_FLOATING_SHOW_MODE = stringPreferencesKey("floating_show_mode")
        val KEY_GALLERY_GRID_VIEW = booleanPreferencesKey("gallery_grid_view")
    }

    val fpsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_FPS] ?: com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getMaxSupportedFps(context)
    }

    val resolutionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_RESOLUTION] ?: "Original"
    }

    val bitrateFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_BITRATE] ?: com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getMaxSupportedBitrate().coerceAtMost(25000000)
    }

    val audioSourceFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUDIO_SOURCE] ?: "Mic"
    }

    val countdownFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_COUNTDOWN] ?: 3
    }

    val showFloatingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHOW_FLOATING] ?: true
    }

    val hideDuringRecordFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_HIDE_DURING_RECORD] ?: false
    }

    val saveLocationFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAVE_LOCATION] ?: "Movies/ScreenX"
    }

    val adaptiveThemeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADAPTIVE_THEME] ?: false
    }

    val shakeToStopFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SHAKE_TO_STOP] ?: false
    }

    val orientationFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ORIENTATION] ?: "Auto"
    }

    val floatingShowModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_FLOATING_SHOW_MODE] ?: "Only when recording"
    }

    val galleryGridViewFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_GALLERY_GRID_VIEW] ?: true
    }

    suspend fun setFps(fps: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FPS] = fps
        }
    }

    suspend fun setResolution(resolution: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RESOLUTION] = resolution
        }
    }

    suspend fun setBitrate(bitrate: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BITRATE] = bitrate
        }
    }

    suspend fun setAudioSource(source: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUDIO_SOURCE] = source
        }
    }

    suspend fun setCountdown(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_COUNTDOWN] = seconds
        }
    }

    suspend fun setShowFloating(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_FLOATING] = show
        }
    }

    suspend fun setHideDuringRecord(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HIDE_DURING_RECORD] = hide
        }
    }

    suspend fun setSaveLocation(location: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAVE_LOCATION] = location
        }
    }

    suspend fun setAdaptiveTheme(adaptive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADAPTIVE_THEME] = adaptive
        }
    }

    suspend fun setShakeToStop(shake: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHAKE_TO_STOP] = shake
        }
    }

    suspend fun setOrientation(orientation: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ORIENTATION] = orientation
        }
    }

    suspend fun setFloatingShowMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FLOATING_SHOW_MODE] = mode
        }
    }

    suspend fun setGalleryGridView(isGridView: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GALLERY_GRID_VIEW] = isGridView
        }
    }
}
