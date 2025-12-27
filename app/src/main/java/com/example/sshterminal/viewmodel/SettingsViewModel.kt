package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val fontSize: Float = 14f,
    val keepScreenOn: Boolean = true,
    val vibrateFeedback: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val terminalColorScheme: TerminalColorScheme = TerminalColorScheme.DEFAULT,
    val connectionTimeout: Int = 30,
    val keepAliveInterval: Int = 60,
    val autoTmux: Boolean = false,
    val tmuxSessionName: String = "main"
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.fontSize.collect { value ->
                _state.value = _state.value.copy(fontSize = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.keepScreenOn.collect { value ->
                _state.value = _state.value.copy(keepScreenOn = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.vibrateFeedback.collect { value ->
                _state.value = _state.value.copy(vibrateFeedback = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { value ->
                _state.value = _state.value.copy(themeMode = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.terminalColorScheme.collect { value ->
                _state.value = _state.value.copy(terminalColorScheme = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionTimeout.collect { value ->
                _state.value = _state.value.copy(connectionTimeout = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.keepAliveInterval.collect { value ->
                _state.value = _state.value.copy(keepAliveInterval = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.autoTmux.collect { value ->
                _state.value = _state.value.copy(autoTmux = value)
            }
        }
        viewModelScope.launch {
            settingsRepository.tmuxSessionName.collect { value ->
                _state.value = _state.value.copy(tmuxSessionName = value)
            }
        }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch {
            settingsRepository.setFontSize(size)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setVibrateFeedback(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrateFeedback(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setTerminalColorScheme(scheme: TerminalColorScheme) {
        viewModelScope.launch {
            settingsRepository.setTerminalColorScheme(scheme)
        }
    }

    fun setConnectionTimeout(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setConnectionTimeout(seconds)
        }
    }

    fun setKeepAliveInterval(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setKeepAliveInterval(seconds)
        }
    }

    fun setAutoTmux(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoTmux(enabled)
        }
    }

    fun setTmuxSessionName(name: String) {
        viewModelScope.launch {
            settingsRepository.setTmuxSessionName(name)
        }
    }
}
