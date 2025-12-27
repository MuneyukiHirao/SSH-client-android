package com.example.sshterminal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sshterminal.data.local.dao.ActiveSessionDao
import com.example.sshterminal.data.local.dao.HostDao
import com.example.sshterminal.data.local.dao.PortForwardDao
import com.example.sshterminal.domain.model.ActiveSession
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.model.PortForward

@Database(
    entities = [Host::class, PortForward::class, ActiveSession::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun hostDao(): HostDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun activeSessionDao(): ActiveSessionDao

    companion object {
        private const val DATABASE_NAME = "ssh_terminal.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add active_sessions table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS active_sessions (
                        sessionId TEXT PRIMARY KEY NOT NULL,
                        hostId INTEGER NOT NULL,
                        hostName TEXT NOT NULL,
                        terminalBuffer TEXT NOT NULL DEFAULT '',
                        cursorX INTEGER NOT NULL DEFAULT 0,
                        cursorY INTEGER NOT NULL DEFAULT 0,
                        scrollbackLines INTEGER NOT NULL DEFAULT 0,
                        isConnected INTEGER NOT NULL DEFAULT 0,
                        lastConnectedAt INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (hostId) REFERENCES hosts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_active_sessions_hostId ON active_sessions(hostId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
