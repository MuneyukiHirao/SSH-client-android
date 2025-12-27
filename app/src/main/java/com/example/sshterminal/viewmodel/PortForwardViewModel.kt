package com.example.sshterminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sshterminal.data.local.dao.PortForwardDao
import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.model.PortForwardType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PortForwardViewModel(
    private val portForwardDao: PortForwardDao
) : ViewModel() {

    private val _portForwards = MutableStateFlow<List<PortForward>>(emptyList())
    val portForwards: StateFlow<List<PortForward>> = _portForwards.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentHostId: Long = 0

    fun loadPortForwards(hostId: Long) {
        currentHostId = hostId
        viewModelScope.launch {
            portForwardDao.getPortForwardsForHost(hostId).collect { forwards ->
                _portForwards.value = forwards
            }
        }
    }

    fun addPortForward(
        type: PortForwardType,
        localPort: Int,
        remoteHost: String = "localhost",
        remotePort: Int = 0,
        name: String? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val portForward = PortForward(
                    hostId = currentHostId,
                    type = type,
                    localPort = localPort,
                    remoteHost = remoteHost,
                    remotePort = remotePort,
                    name = name
                )
                portForwardDao.insertPortForward(portForward)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePortForward(portForward: PortForward) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                portForwardDao.updatePortForward(portForward)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePortForward(portForward: PortForward) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                portForwardDao.deletePortForward(portForward)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                portForwardDao.setEnabled(id, enabled)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
