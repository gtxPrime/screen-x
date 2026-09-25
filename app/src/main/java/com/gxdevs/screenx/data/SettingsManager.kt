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
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_SHAKE_TO_STOP = booleanPreferencesKey("shake_to_stop")
        val KEY_ORIENTATION = stringPreferencesKey("orientation")
        val KEY_FLOATING_SHOW_MODE = stringPreferencesKey("floating_show_mode")
        val KEY_GALLERY_GRID_VIEW = booleanPreferencesKey("gallery_grid_view")
        val KEY_SAFE_STORAGE_STOP = booleanPreferencesKey("safe_storage_stop")
        val KEY_SAFE_STORAGE_THRESHOLD_MB = intPreferencesKey("safe_storage_threshold_mb")
        // ADB capture: "mediaprojection" | "adb" | "ask"
        val KEY_ADB_CAPTURE_MODE = stringPreferencesKey("adb_capture_mode")
        val KEY_ADB_ENABLED      = booleanPreferencesKey("adb_enabled")
        // ADB wireless connection state
        val KEY_ADB_PORT         = intPreferencesKey("adb_port")         // wireless-debug connection port
        val KEY_ADB_PAIRED       = booleanPreferencesKey("adb_paired")   // true once paired successfully
        val KEY_ADB_PRIVATE_KEY  = stringPreferencesKey("adb_private_key") // Base64 RSA private key
        val KEY_ADB_PUBLIC_KEY   = stringPreferencesKey("adb_public_key")  // Base64 RSA public key
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

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_THEME_MODE] ?: "system"
    }

    val adaptiveThemeFlow: Flow<Boolean> = themeModeFlow.map { it == "dynamic" }

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

    val safeStorageStopFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAFE_STORAGE_STOP] ?: true
    }

    val safeStorageThresholdMbFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAFE_STORAGE_THRESHOLD_MB] ?: 150
    }

    /** "mediaprojection" | "adb" | "ask" — default is MediaProjection (standard) */
    val adbCaptureModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_CAPTURE_MODE] ?: "mediaprojection"
    }

    val adbEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_ENABLED] ?: false
    }

    val adbPortFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_PORT] ?: 0
    }

    val adbPairedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_PAIRED] ?: false
    }

    val adbPrivateKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_PRIVATE_KEY] ?: ""
    }

    val adbPublicKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ADB_PUBLIC_KEY] ?: ""
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

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setAdaptiveTheme(adaptive: Boolean) {
        setThemeMode(if (adaptive) "dynamic" else "system")
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

    suspend fun setSafeStorageStop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFE_STORAGE_STOP] = enabled
        }
    }

    suspend fun setSafeStorageThresholdMb(thresholdMb: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SAFE_STORAGE_THRESHOLD_MB] = thresholdMb
        }
    }

    suspend fun setAdbEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADB_ENABLED] = enabled
        }
    }

    suspend fun setAdbCaptureMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADB_CAPTURE_MODE] = mode
        }
    }

    suspend fun setAdbPort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADB_PORT] = port
        }
    }

    suspend fun setAdbPaired(paired: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADB_PAIRED] = paired
        }
    }

    suspend fun setAdbKeys(privateKeyB64: String, publicKeyB64: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ADB_PRIVATE_KEY] = privateKeyB64
            preferences[KEY_ADB_PUBLIC_KEY]  = publicKeyB64
        }
    }
}
