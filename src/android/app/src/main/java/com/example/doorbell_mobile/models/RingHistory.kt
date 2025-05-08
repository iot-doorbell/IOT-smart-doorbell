package com.example.doorbell_mobile.models

import java.time.Duration

data class RingHistory(
    val startTime: String,
    val endTime: String,
    val status: CallStatus
)

enum class CallStatus {
    ACCEPTED, REJECTED, MISSED
}