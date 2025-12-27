package com.example.sshterminal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sshterminal.domain.model.Host
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {

    @Query("SELECT * FROM hosts ORDER BY CASE WHEN lastConnected IS NULL THEN 1 ELSE 0 END, lastConnected DESC, name ASC")
    fun getAllHosts(): Flow<List<Host>>

    @Query("SELECT * FROM hosts WHERE id = :id")
    suspend fun getHostById(id: Long): Host?

    @Query("SELECT * FROM hosts WHERE id = :id")
    fun getHostByIdFlow(id: Long): Flow<Host?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: Host): Long

    @Update
    suspend fun updateHost(host: Host)

    @Delete
    suspend fun deleteHost(host: Host)

    @Query("UPDATE hosts SET lastConnected = :timestamp WHERE id = :hostId")
    suspend fun updateLastConnected(hostId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE hosts SET hostKeyFingerprint = :fingerprint WHERE id = :hostId")
    suspend fun updateHostKeyFingerprint(hostId: Long, fingerprint: String)

    @Query("SELECT COUNT(*) FROM hosts")
    suspend fun getHostCount(): Int
}
