package com.example.sshterminal.data.repository

import com.example.sshterminal.data.local.dao.HostDao
import com.example.sshterminal.domain.model.Host
import com.example.sshterminal.domain.repository.HostRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of HostRepository using Room DAO.
 */
class HostRepositoryImpl(
    private val hostDao: HostDao
) : HostRepository {

    override fun getAllHosts(): Flow<List<Host>> {
        return hostDao.getAllHosts()
    }

    override suspend fun getHostById(id: Long): Host? {
        return hostDao.getHostById(id)
    }

    override suspend fun insertHost(host: Host): Long {
        return hostDao.insertHost(host)
    }

    override suspend fun updateHost(host: Host) {
        hostDao.updateHost(host)
    }

    override suspend fun deleteHost(host: Host) {
        hostDao.deleteHost(host)
    }

    override suspend fun updateLastConnected(hostId: Long) {
        hostDao.updateLastConnected(hostId)
    }

    override suspend fun updateHostKeyFingerprint(hostId: Long, fingerprint: String) {
        hostDao.updateHostKeyFingerprint(hostId, fingerprint)
    }
}
