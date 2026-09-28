package com.analoganchor.app.worker

import android.content.Context
import android.net.Uri

class BackupManager(private val context: Context) {
    suspend fun scheduleBackup(intervalDays: Int, uri: String?): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun cancelBackup(): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun runBackupNow(uri: String?): Result<Unit> {
        return Result.success(Unit)
    }
}
