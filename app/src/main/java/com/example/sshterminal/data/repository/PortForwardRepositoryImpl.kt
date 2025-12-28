package com.example.sshterminal.data.repository

import com.example.sshterminal.data.local.dao.PortForwardDao
import com.example.sshterminal.domain.model.PortForward
import com.example.sshterminal.domain.repository.PortForwardRepository
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of PortForwardRepository using Room DAO.
 */
class PortForwardRepositoryImpl(
    private val portForwardDao: PortForwardDao
) : PortForwardRepository {

    override fun getPortForwardsForHost(hostId: Long): Flow<List<PortForward>> {
        return portForwardDao.getPortForwardsForHost(hostId)
    }

    override suspend fun getEnabledPortForwards(hostId: Long): List<PortForward> {
        return portForwardDao.getEnabledPortForwards(hostId)
    }

    override suspend fun insertPortForward(portForward: PortForward) {
        portForwardDao.insertPortForward(portForward)
    }

    override suspend fun updatePortForward(portForward: PortForward) {
        portForwardDao.updatePortForward(portForward)
    }

    override suspend fun deletePortForward(portForward: PortForward) {
        portForwardDao.deletePortForward(portForward)
    }
}
