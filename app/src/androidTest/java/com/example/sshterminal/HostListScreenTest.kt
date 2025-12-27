package com.example.sshterminal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sshterminal.domain.model.AuthMethod
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.ui.screens.HostListScreen
import com.example.sshterminal.viewmodel.HostViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * UI tests for HostListScreen
 */
@RunWith(AndroidJUnit4::class)
class HostListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createMockViewModel(hosts: List<Host> = emptyList()): HostViewModel {
        val viewModel: HostViewModel = mock()
        whenever(viewModel.hosts).thenReturn(MutableStateFlow(hosts))
        return viewModel
    }

    @Test
    fun hostListScreen_displaysTitle() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("SSH Terminal").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_displaysEmptyState() {
        val viewModel = createMockViewModel(emptyList())

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("No hosts configured").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Host").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_displaysSingleHost() {
        val testHost = Host(
            id = 1,
            name = "Test Server",
            hostname = "192.168.1.1",
            port = 22,
            username = "testuser",
            authMethod = AuthMethod.PASSWORD
        )
        val viewModel = createMockViewModel(listOf(testHost))

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Test Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("testuser@192.168.1.1:22").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_displaysMultipleHosts() {
        val hosts = listOf(
            Host(
                id = 1,
                name = "Server 1",
                hostname = "server1.example.com",
                port = 22,
                username = "user1",
                authMethod = AuthMethod.PASSWORD
            ),
            Host(
                id = 2,
                name = "Server 2",
                hostname = "server2.example.com",
                port = 2222,
                username = "user2",
                authMethod = AuthMethod.PUBLIC_KEY,
                keyName = "my-key"
            )
        )
        val viewModel = createMockViewModel(hosts)

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Server 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server 2").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_displaysFabButton() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Add host").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_fabClickNavigatesToHostEdit() {
        val viewModel = createMockViewModel()
        var navigatedToEdit = false

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = { navigatedToEdit = true },
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Add host").performClick()
        assert(navigatedToEdit)
    }

    @Test
    fun hostListScreen_hostClickNavigatesToTerminal() {
        val testHost = Host(
            id = 1,
            name = "Test Server",
            hostname = "192.168.1.1",
            port = 22,
            username = "testuser",
            authMethod = AuthMethod.PASSWORD
        )
        val viewModel = createMockViewModel(listOf(testHost))
        var navigatedHostId: Long? = null

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = { navigatedHostId = it },
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Test Server").performClick()
        assert(navigatedHostId == 1L)
    }

    @Test
    fun hostListScreen_displaysHostWithPublicKeyAuth() {
        val testHost = Host(
            id = 1,
            name = "Secure Server",
            hostname = "secure.example.com",
            port = 22,
            username = "admin",
            authMethod = AuthMethod.PUBLIC_KEY,
            keyName = "ed25519-key"
        )
        val viewModel = createMockViewModel(listOf(testHost))

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Secure Server").assertIsDisplayed()
    }

    @Test
    fun hostListScreen_displaysMenuOptions() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            HostListScreen(
                viewModel = viewModel,
                onNavigateToHostEdit = {},
                onNavigateToTerminal = {},
                onNavigateToKeyManager = {},
                onNavigateToSettings = {}
            )
        }

        // Menu icon should be displayed
        composeTestRule.onNodeWithContentDescription("Menu").assertIsDisplayed()
    }
}
