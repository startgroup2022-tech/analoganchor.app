package com.analoganchor.app.data.remote.model

data class GitHubRelease(
    val tagName: String = "v1.0.0",
    val publishedAt: String = "",
    val htmlUrl: String = "",
    val body: String = ""
)
