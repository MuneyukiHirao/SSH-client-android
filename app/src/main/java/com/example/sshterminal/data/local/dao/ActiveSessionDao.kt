package com.example.sshterminal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sshterminal.domain.model.ActiveSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveSessionDao {

    @Query("SELECT * FROM active_sessions ORDER BY createdAt ASC")
    fun getAllSessions(): Flow<List<ActiveSession>>

    @Query("SELECT * FROM active_sessions ORDER BY createdAt ASC")
    suspend fun getAllSessionsOnce(): List<ActiveSession>

    @Query("SELECT * FROM active_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): ActiveSession?

    @Query("SELECT * FROM active_sessions WHERE hostId = :hostId")
    suspend fun getSessionsByHost(hostId: Long): List<ActiveSession>

    @Query("SELECT * FROM active_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): ActiveSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ActiveSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<ActiveSession>)

    @Update
    suspend fun updateSession(session: ActiveSession)

    @Query("UPDATE active_sessions SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE active_sessions SET isActive = 1 WHERE sessionId = :sessionId")
    suspend fun setActiveSession(sessionId: String)

    @Query("UPDATE active_sessions SET terminalBuffer = :buffer, cursorX = :cursorX, cursorY = :cursorY, scrollbackLines = :scrollbackLines WHERE sessionId = :sessionId")
    suspend fun updateTerminalState(
        sessionId: String,
        buffer: String,
        cursorX: Int,
        cursorY: Int,
        scrollbackLines: Int
    )

    @Query("UPDATE active_sessions SET isConnected = :isConnected, lastConnectedAt = :lastConnectedAt, lastError = :lastError WHERE sessionId = :sessionId")
    suspend fun updateConnectionState(
        sessionId: String,
        isConnected: Boolean,
        lastConnectedAt: Long,
        lastError: String?
    )

    @Delete
    suspend fun deleteSession(session: ActiveSession)

    @Query("DELETE FROM active_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM active_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM active_sessions")
    suspend fun getSessionCount(): Int
}
