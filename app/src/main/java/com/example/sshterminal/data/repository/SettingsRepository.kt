package com.example.sshterminal.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VIBRATE_FEEDBACK = booleanPreferencesKey("vibrate_feedback")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val TERMINAL_COLOR_SCHEME = intPreferencesKey("terminal_color_scheme")
        val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        val KEEP_ALIVE_INTERVAL = intPreferencesKey("keep_alive_interval")
        val AUTO_TMUX = booleanPreferencesKey("auto_tmux")
        val TMUX_SESSION_NAME = stringPreferencesKey("tmux_session_name")
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FONT_SIZE] ?: 14f
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true
    }

    val vibrateFeedback: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIBRATE_FEEDBACK] ?: true
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        ThemeMode.fromValue(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.value)
    }

    val terminalColorScheme: Flow<TerminalColorScheme> = context.dataStore.data.map { preferences ->
        TerminalColorScheme.fromValue(preferences[PreferencesKeys.TERMINAL_COLOR_SCHEME] ?: TerminalColorScheme.DEFAULT.value)
    }

    val connectionTimeout: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CONNECTION_TIMEOUT] ?: 30
    }

    val keepAliveInterval: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEEP_ALIVE_INTERVAL] ?: 60
    }

    val autoTmux: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_TMUX] ?: false
    }

    val tmuxSessionName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TMUX_SESSION_NAME] ?: "main"
    }

    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE] = size
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setVibrateFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATE_FEEDBACK] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.value
        }
    }

    suspend fun setTerminalColorScheme(scheme: TerminalColorScheme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TERMINAL_COLOR_SCHEME] = scheme.value
        }
    }

    suspend fun setConnectionTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONNECTION_TIMEOUT] = seconds
        }
    }

    suspend fun setKeepAliveInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_ALIVE_INTERVAL] = seconds
        }
    }

    suspend fun setAutoTmux(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_TMUX] = enabled
        }
    }

    suspend fun setTmuxSessionName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TMUX_SESSION_NAME] = name
        }
    }
}
