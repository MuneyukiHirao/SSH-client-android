package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.local.dao.HostDao
import com.example.sshterminal.domain.model.Host
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HostViewModel(
    private val hostDao: HostDao
) : ViewModel() {

    val hosts = hostDao.getAllHosts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedHost = MutableStateFlow<Host?>(null)
    val selectedHost: StateFlow<Host?> = _selectedHost.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun selectHost(host: Host) {
        _selectedHost.value = host
    }

    fun clearSelection() {
        _selectedHost.value = null
    }

    fun addHost(host: Host) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                hostDao.insertHost(host)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateHost(host: Host) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                hostDao.updateHost(host)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteHost(host: Host) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                hostDao.deleteHost(host)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getHostById(id: Long, onResult: (Host?) -> Unit) {
        viewModelScope.launch {
            val host = hostDao.getHostById(id)
            onResult(host)
        }
    }

    fun updateLastConnected(hostId: Long) {
        viewModelScope.launch {
            hostDao.updateLastConnected(hostId)
        }
    }

    fun updateHostKeyFingerprint(hostId: Long, fingerprint: String) {
        viewModelScope.launch {
            hostDao.updateHostKeyFingerprint(hostId, fingerprint)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
