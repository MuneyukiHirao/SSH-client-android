package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.ssh.KeyStoreManager
import com.example.sshterminal.domain.model.KeyType
import com.example.sshterminal.domain.model.SSHKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeyViewModel(
    private val keyStoreManager: KeyStoreManager
) : ViewModel() {

    private val _keys = MutableStateFlow<List<SSHKey>>(emptyList())
    val keys: StateFlow<List<SSHKey>> = _keys.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _generatedKey = MutableStateFlow<SSHKey?>(null)
    val generatedKey: StateFlow<SSHKey?> = _generatedKey.asStateFlow()

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _keys.value = keyStoreManager.getAllKeys()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateKey(name: String, keyType: KeyType) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val result = keyStoreManager.generateKey(name, keyType)
                result.fold(
                    onSuccess = { key ->
                        _generatedKey.value = key
                        loadKeys()
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Key generation failed"
                    }
                )
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteKey(alias: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = keyStoreManager.deleteKey(alias)
                if (success) {
                    loadKeys()
                } else {
                    _error.value = "Failed to delete key"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearGeneratedKey() {
        _generatedKey.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun getKeyByAlias(alias: String): SSHKey? {
        return _keys.value.find { it.alias == alias }
    }
}
