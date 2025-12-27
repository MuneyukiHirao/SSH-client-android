package com.example.sshterminal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.domain.model.TerminalColorScheme
import com.example.sshterminal.domain.model.ThemeMode
import com.example.sshterminal.ui.screens.SettingsScreen
import com.example.sshterminal.viewmodel.SettingsState
import com.example.sshterminal.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * UI tests for SettingsScreen
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMockViewModel(state: SettingsState = SettingsState()): SettingsViewModel {
        val viewModel: SettingsViewModel = mock()
        whenever(viewModel.state).thenReturn(MutableStateFlow(state))
        return viewModel
    }

    @Test
    fun settingsScreen_displaysAllSections() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        // Verify main sections are displayed
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeTestRule.onNodeWithText("Terminal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connection").assertIsDisplayed()
        composeTestRule.onNodeWithText("tmux").assertIsDisplayed()
        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysAppearanceSettings() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysTerminalSettings() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Color Scheme").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep Screen On").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vibrate on Key Press").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysTmuxSettings() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Auto tmux").assertIsDisplayed()
        composeTestRule.onNodeWithText("Session Name").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysConnectionSettings() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Connection Timeout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep Alive Interval").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysVersionInfo() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Version").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0.0").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_keepScreenOnToggle_displaysCorrectState() {
        val viewModel = createMockViewModel(
            SettingsState(keepScreenOn = true)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Keep Screen On").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_autoTmuxToggle_displaysCorrectState() {
        val viewModel = createMockViewModel(
            SettingsState(autoTmux = false)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Auto tmux").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysFontSizeValue() {
        val viewModel = createMockViewModel(
            SettingsState(fontSize = 16f)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Font Size").assertIsDisplayed()
        composeTestRule.onNodeWithText("16.0").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysConnectionTimeoutValue() {
        val viewModel = createMockViewModel(
            SettingsState(connectionTimeout = 30)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("30 seconds").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysKeepAliveIntervalValue() {
        val viewModel = createMockViewModel(
            SettingsState(keepAliveInterval = 60)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("60 seconds").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_displaysTmuxSessionName() {
        val viewModel = createMockViewModel(
            SettingsState(tmuxSessionName = "main")
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Session Name").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_themeSelection_showsCurrentTheme() {
        val viewModel = createMockViewModel(
            SettingsState(themeMode = ThemeMode.DARK)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_colorSchemeSelection_showsCurrentScheme() {
        val viewModel = createMockViewModel(
            SettingsState(terminalColorScheme = TerminalColorScheme.DRACULA)
        )

        composeTestRule.setContent {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithText("Dracula").assertIsDisplayed()
    }
}
