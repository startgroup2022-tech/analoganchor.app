package com.analoganchor.app.data

import kotlinx.coroutines.flow.Flow

class SessionLogRepository(private val dao: SessionLogDao) {
    fun getLatestLogs(limit: Int): Flow<List<SessionLog>> {
        return dao.getLatestLogs(limit)
    }

    fun getWhiteboardSessions(): Flow<List<SessionLog>> {
        return dao.getWhiteboardSessions()
    }

    suspend fun insertLog(log: SessionLog) {
        dao.insertLog(log)
    }

    suspend fun clearLogs() {
        dao.clearLogs()
    }
}
