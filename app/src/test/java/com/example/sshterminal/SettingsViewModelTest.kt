package com.example.sshterminal

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.ThemeMode
import com.example.sshterminal.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for SettingsViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    // Mock flows for repository
    private val fontSizeFlow = MutableStateFlow(14f)
    private val keepScreenOnFlow = MutableStateFlow(true)
    private val vibrateFeedbackFlow = MutableStateFlow(true)
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val terminalColorSchemeFlow = MutableStateFlow(TerminalColorScheme.DEFAULT)
    private val connectionTimeoutFlow = MutableStateFlow(30)
    private val keepAliveIntervalFlow = MutableStateFlow(60)
    private val autoTmuxFlow = MutableStateFlow(false)
    private val tmuxSessionNameFlow = MutableStateFlow("main")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        settingsRepository = mock()

        whenever(settingsRepository.fontSize).thenReturn(fontSizeFlow)
        whenever(settingsRepository.keepScreenOn).thenReturn(keepScreenOnFlow)
        whenever(settingsRepository.vibrateFeedback).thenReturn(vibrateFeedbackFlow)
        whenever(settingsRepository.themeMode).thenReturn(themeModeFlow)
        whenever(settingsRepository.terminalColorScheme).thenReturn(terminalColorSchemeFlow)
        whenever(settingsRepository.connectionTimeout).thenReturn(connectionTimeoutFlow)
        whenever(settingsRepository.keepAliveInterval).thenReturn(keepAliveIntervalFlow)
        whenever(settingsRepository.autoTmux).thenReturn(autoTmuxFlow)
        whenever(settingsRepository.tmuxSessionName).thenReturn(tmuxSessionNameFlow)

        viewModel = SettingsViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default values`() = runTest {
        advanceUntilIdle()

        val state = viewModel.state.value

        assertEquals(14f, state.fontSize)
        assertEquals(true, state.keepScreenOn)
        assertEquals(true, state.vibrateFeedback)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(TerminalColorScheme.DEFAULT, state.terminalColorScheme)
        assertEquals(30, state.connectionTimeout)
        assertEquals(60, state.keepAliveInterval)
        assertEquals(false, state.autoTmux)
        assertEquals("main", state.tmuxSessionName)
    }

    @Test
    fun `state updates when fontSize changes`() = runTest {
        fontSizeFlow.value = 18f
        advanceUntilIdle()

        assertEquals(18f, viewModel.state.value.fontSize)
    }

    @Test
    fun `state updates when themeMode changes`() = runTest {
        themeModeFlow.value = ThemeMode.DARK
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.state.value.themeMode)
    }

    @Test
    fun `state updates when terminalColorScheme changes`() = runTest {
        terminalColorSchemeFlow.value = TerminalColorScheme.DRACULA
        advanceUntilIdle()

        assertEquals(TerminalColorScheme.DRACULA, viewModel.state.value.terminalColorScheme)
    }

    @Test
    fun `state updates when autoTmux changes`() = runTest {
        autoTmuxFlow.value = true
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.autoTmux)
    }

    @Test
    fun `state updates when tmuxSessionName changes`() = runTest {
        tmuxSessionNameFlow.value = "custom"
        advanceUntilIdle()

        assertEquals("custom", viewModel.state.value.tmuxSessionName)
    }

    @Test
    fun `setFontSize calls repository`() = runTest {
        viewModel.setFontSize(20f)
        advanceUntilIdle()

        verify(settingsRepository).setFontSize(20f)
    }

    @Test
    fun `setKeepScreenOn calls repository`() = runTest {
        viewModel.setKeepScreenOn(false)
        advanceUntilIdle()

        verify(settingsRepository).setKeepScreenOn(false)
    }

    @Test
    fun `setVibrateFeedback calls repository`() = runTest {
        viewModel.setVibrateFeedback(false)
        advanceUntilIdle()

        verify(settingsRepository).setVibrateFeedback(false)
    }

    @Test
    fun `setThemeMode calls repository`() = runTest {
        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        verify(settingsRepository).setThemeMode(ThemeMode.LIGHT)
    }

    @Test
    fun `setTerminalColorScheme calls repository`() = runTest {
        viewModel.setTerminalColorScheme(TerminalColorScheme.NORD)
        advanceUntilIdle()

        verify(settingsRepository).setTerminalColorScheme(TerminalColorScheme.NORD)
    }

    @Test
    fun `setConnectionTimeout calls repository`() = runTest {
        viewModel.setConnectionTimeout(45)
        advanceUntilIdle()

        verify(settingsRepository).setConnectionTimeout(45)
    }

    @Test
    fun `setKeepAliveInterval calls repository`() = runTest {
        viewModel.setKeepAliveInterval(90)
        advanceUntilIdle()

        verify(settingsRepository).setKeepAliveInterval(90)
    }

    @Test
    fun `setAutoTmux calls repository`() = runTest {
        viewModel.setAutoTmux(true)
        advanceUntilIdle()

        verify(settingsRepository).setAutoTmux(true)
    }

    @Test
    fun `setTmuxSessionName calls repository`() = runTest {
        viewModel.setTmuxSessionName("mySession")
        advanceUntilIdle()

        verify(settingsRepository).setTmuxSessionName("mySession")
    }

    @Test
    fun `multiple settings updates are reflected in state`() = runTest {
        fontSizeFlow.value = 16f
        themeModeFlow.value = ThemeMode.DARK
        terminalColorSchemeFlow.value = TerminalColorScheme.MONOKAI
        autoTmuxFlow.value = true
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(16f, state.fontSize)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(TerminalColorScheme.MONOKAI, state.terminalColorScheme)
        assertEquals(true, state.autoTmux)
    }
}
