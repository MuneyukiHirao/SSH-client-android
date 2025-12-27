package com.example.sshterminal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.sshterminal.domain.model.PortForward
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardDao {

    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId ORDER BY type, localPort")
    fun getPortForwardsForHost(hostId: Long): Flow<List<PortForward>>

    @Query("SELECT * FROM port_forwards WHERE hostId = :hostId AND enabled = 1")
    suspend fun getEnabledPortForwards(hostId: Long): List<PortForward>

    @Query("SELECT * FROM port_forwards WHERE id = :id")
    suspend fun getPortForwardById(id: Long): PortForward?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortForward(portForward: PortForward): Long

    @Update
    suspend fun updatePortForward(portForward: PortForward)

    @Delete
    suspend fun deletePortForward(portForward: PortForward)

    @Query("DELETE FROM port_forwards WHERE hostId = :hostId")
    suspend fun deleteAllForHost(hostId: Long)

    @Query("UPDATE port_forwards SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
