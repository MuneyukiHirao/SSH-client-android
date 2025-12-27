package com.example.sshterminal.di

import com.example.sshterminal.data.local.AppDatabase
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.data.ssh.KeyStoreManager
import com.example.sshterminal.data.ssh.SessionManager
import com.example.sshterminal.data.ssh.SSHConnectionService
import com.example.sshterminal.viewmodel.HostViewModel
import com.example.sshterminal.viewmodel.KeyViewModel
import com.example.sshterminal.viewmodel.MultiSessionViewModel
import com.example.sshterminal.viewmodel.PortForwardViewModel
import com.example.sshterminal.viewmodel.SettingsViewModel
import com.example.sshterminal.viewmodel.TerminalViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getInstance(androidContext()) }
    single { get<AppDatabase>().hostDao() }
    single { get<AppDatabase>().portForwardDao() }
    single { get<AppDatabase>().activeSessionDao() }

    // Repositories
    single { SettingsRepository(androidContext()) }

    // Managers
    single { KeyStoreManager(androidContext()) }

    // Service connection holder
    single { ServiceConnectionHolder() }

    // ViewModels
    viewModel { HostViewModel(get()) }
    viewModel { KeyViewModel(get()) }
    viewModel { TerminalViewModel(get(), get(), get()) }
    viewModel { PortForwardViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { MultiSessionViewModel(get(), get(), get()) }
}

/**
 * Holds the connection to SSHConnectionService.
 * This allows ViewModels to access the SessionManager from the service.
 */
class ServiceConnectionHolder {
    private var service: SSHConnectionService? = null

    val sessionManager: SessionManager?
        get() = service?.sessionManager

    val isConnected: Boolean
        get() = service != null

    fun setService(service: SSHConnectionService?) {
        this.service = service
    }

    fun getService(): SSHConnectionService? = service
}
