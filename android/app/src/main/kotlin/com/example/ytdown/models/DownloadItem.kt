package com.example.ytdown.models

data class DownloadItem(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val progress: Double = 0.0,
    val status: String = "pending"
)
