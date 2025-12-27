package com.example.sshterminal.data.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sshterminal.MainActivity
import com.example.sshterminal.R
import com.example.sshterminal.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains SSH connections in the background.
 * This service owns the SessionManager and keeps connections alive when the app is backgrounded.
 */
class SSHConnectionService : Service() {

    companion object {
        private const val TAG = "SSHConnectionService"
        private const val CHANNEL_ID = "ssh_connection_channel"
        private const val NOTIFICATION_ID = 1
        private const val WAKELOCK_TAG = "SSHTerminal:SSHConnectionService"

        // Actions
        const val ACTION_START = "com.example.sshterminal.START_SERVICE"
        const val ACTION_STOP = "com.example.sshterminal.STOP_SERVICE"

        /**
         * Start the service
         */
        fun start(context: Context) {
            val intent = Intent(context, SSHConnectionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the service
         */
        fun stop(context: Context) {
            val intent = Intent(context, SSHConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // SessionManager is owned by this service
    private var _sessionManager: SessionManager? = null
    val sessionManager: SessionManager
        get() = _sessionManager ?: throw IllegalStateException("Service not initialized")

    // WakeLock for keeping connections alive
    private var wakeLock: PowerManager.WakeLock? = null

    // Network monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Foreground/background state
    private val _isInForeground = MutableStateFlow(true)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    // Network state
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): SSHConnectionService = this@SSHConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        setupNetworkMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }

        return START_STICKY
    }

    /**
     * Initialize the SessionManager with KeyStoreManager, ActiveSessionDao, and SettingsRepository
     */
    fun initializeSessionManager(
        keyStoreManager: KeyStoreManager,
        activeSessionDao: com.example.sshterminal.data.local.dao.ActiveSessionDao,
        settingsRepository: SettingsRepository
    ) {
        if (_sessionManager == null) {
            _sessionManager = SessionManager(keyStoreManager, activeSessionDao, settingsRepository)
            Log.d(TAG, "SessionManager initialized")

            // Update notification when session list changes
            serviceScope.launch {
                sessionManager.sessionList.collect {
                    updateNotification()
                }
            }

            // Restore persisted sessions
            serviceScope.launch {
                sessionManager.restorePersistedSessions()
            }
        }
    }

    /**
     * Called when the app moves to foreground
     */
    fun onAppForeground() {
        Log.d(TAG, "App moved to foreground")
        _isInForeground.value = true
        releaseWakeLock()
    }

    /**
     * Called when the app moves to background
     */
    fun onAppBackground() {
        Log.d(TAG, "App moved to background")
        _isInForeground.value = false
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }

        val sessionCount = _sessionManager?.getSessionCount() ?: 0
        if (sessionCount > 0 && wakeLock?.isHeld != true) {
            // Acquire wake lock for up to 10 minutes
            wakeLock?.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _isNetworkAvailable.value = true
                _sessionManager?.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _isNetworkAvailable.value = false
                _sessionManager?.onNetworkLost()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                if (hasInternet != _isNetworkAvailable.value) {
                    _isNetworkAvailable.value = hasInternet
                    if (hasInternet) {
                        _sessionManager?.onNetworkAvailable()
                    } else {
                        _sessionManager?.onNetworkLost()
                    }
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSH Connections",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active SSH connections"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val connectionCount = _sessionManager?.sessionList?.value?.count {
            it.state is TerminalSession.SessionState.Connected
        } ?: 0

        val text = if (connectionCount == 0) {
            "No active connections"
        } else {
            "$connectionCount active connection(s)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Terminal")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())

        // Stop service if no connections and app is in background
        val sessionCount = _sessionManager?.getSessionCount() ?: 0
        if (sessionCount == 0 && !_isInForeground.value) {
            Log.d(TAG, "No sessions and app in background, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        // Unregister network callback
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        networkCallback = null

        // Release wake lock
        releaseWakeLock()

        // Cancel service scope
        serviceScope.cancel()

        // Note: We don't close all sessions here to allow reconnection
        // The SessionManager will handle cleanup when needed

        super.onDestroy()
    }
}
