package com.example.ntfypop.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class NtfyAttachment(
    val name: String = "",
    val url: String = "",
    val type: String = "",
    val size: Long = 0L,
)
