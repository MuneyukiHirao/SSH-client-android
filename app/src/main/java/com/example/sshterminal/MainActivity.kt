package com.example.sshterminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.sshterminal.data.local.dao.ActiveSessionDao
import com.example.sshterminal.data.repository.SettingsRepository
import com.example.sshterminal.data.ssh.KeyStoreManager
import com.example.sshterminal.data.ssh.SSHConnectionService
import com.example.sshterminal.di.ServiceConnectionHolder
import com.example.sshterminal.domain.model.ThemeMode
import com.example.sshterminal.ui.SSHTerminalApp
import com.example.sshterminal.ui.theme.SSHTerminalTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val keyStoreManager: KeyStoreManager by inject()
    private val activeSessionDao: ActiveSessionDao by inject()
    private val serviceConnectionHolder: ServiceConnectionHolder by inject()
    private val settingsRepository: SettingsRepository by inject()

    private var sshService: SSHConnectionService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected")
            val service = (binder as SSHConnectionService.LocalBinder).getService()
            sshService = service
            serviceBound = true

            // Initialize SessionManager in the service
            service.initializeSessionManager(keyStoreManager, activeSessionDao, settingsRepository)

            // Update the service connection holder so ViewModels can access it
            serviceConnectionHolder.setService(service)

            // Notify service that app is in foreground
            service.onAppForeground()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            sshService = null
            serviceBound = false
            serviceConnectionHolder.setService(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        enableEdgeToEdge()

        // Start and bind to the service
        SSHConnectionService.start(this)
        bindService(
            Intent(this, SSHConnectionService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            SSHTerminalTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SSHTerminalApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        sshService?.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        sshService?.onAppBackground()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        // Unbind from service (but don't stop it if there are active sessions)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        // Only stop service if finishing and no active sessions
        if (isFinishing) {
            val sessionCount = sshService?.sessionManager?.getSessionCount() ?: 0
            if (sessionCount == 0) {
                SSHConnectionService.stop(this)
            }
        }

        super.onDestroy()
    }
}
