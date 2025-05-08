package com.example.doorbell_mobile.models

data class ChatMessage(
    val text: String,
    val isMine: Boolean,   // true: cột phải, false: cột trái
    val timestamp: Long = System.currentTimeMillis()
)
