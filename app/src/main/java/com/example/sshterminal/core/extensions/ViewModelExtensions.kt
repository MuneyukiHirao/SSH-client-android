package com.example.sshterminal.core.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Interface for ViewModels that support loading and error states
 */
interface LoadingErrorViewModel {
    val isLoading: MutableStateFlow<Boolean>
    val error: MutableStateFlow<String?>
}

/**
 * Extension function for ViewModel to launch a coroutine with loading state management
 * and error handling.
 *
 * @param isLoading MutableStateFlow to track loading state
 * @param error MutableStateFlow to track error messages
 * @param block The suspend block to execute
 */
fun ViewModel.launchWithLoading(
    isLoading: MutableStateFlow<Boolean>,
    error: MutableStateFlow<String?>,
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            isLoading.value = true
            block()
        } catch (e: Exception) {
            error.value = e.message ?: "Unknown error occurred"
        } finally {
            isLoading.value = false
        }
    }
}

/**
 * Extension function for LoadingErrorViewModel to launch a coroutine with
 * automatic loading state management and error handling.
 *
 * @param block The suspend block to execute
 */
fun <T : ViewModel> T.launchWithLoading(
    block: suspend CoroutineScope.() -> Unit
) where T : LoadingErrorViewModel {
    viewModelScope.launch {
        try {
            isLoading.value = true
            block()
        } catch (e: Exception) {
            error.value = e.message ?: "Unknown error occurred"
        } finally {
            isLoading.value = false
        }
    }
}

/**
 * Extension function for ViewModel to launch a coroutine with error handling only
 * (no loading state).
 *
 * @param error MutableStateFlow to track error messages
 * @param block The suspend block to execute
 */
fun ViewModel.launchWithErrorHandling(
    error: MutableStateFlow<String?>,
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            error.value = e.message ?: "Unknown error occurred"
        }
    }
}

/**
 * Extension function for LoadingErrorViewModel to clear the error state
 */
fun LoadingErrorViewModel.clearError() {
    error.value = null
}
