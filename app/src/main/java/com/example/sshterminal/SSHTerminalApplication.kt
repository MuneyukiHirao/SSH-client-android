package com.example.sshterminal

import android.app.Application
import com.example.sshterminal.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class SSHTerminalApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Add BouncyCastle provider for Ed25519 and other advanced crypto
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        // Initialize Koin DI
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@SSHTerminalApplication)
            modules(appModule)
        }
    }
}
