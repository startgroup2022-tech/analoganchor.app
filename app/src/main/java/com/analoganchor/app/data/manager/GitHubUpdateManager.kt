package com.analoganchor.app.data.manager

import android.content.Context
import com.analoganchor.app.data.remote.model.GitHubRelease

class GitHubUpdateManager(private val context: Context) {
    suspend fun fetchLatestRelease(): Result<GitHubRelease> {
        return Result.failure(Exception("Not implemented in Night Guard"))
    }
}
