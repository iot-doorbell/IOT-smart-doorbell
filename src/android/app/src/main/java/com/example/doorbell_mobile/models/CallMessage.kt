package com.example.doorbell_mobile.models

import com.google.gson.annotations.SerializedName


data class CallMessage(
    @SerializedName("status")
    val status: String,    // "calling", "accept", "reject", "missed", ...

    @SerializedName("end_time")
    val endTime: Long?,     // timestamp (ms) kết thúc cuộc gọi

    @SerializedName("caller_name")
    val callerName: String?,// tên người gọi

    @SerializedName("avatar_url")
    val avatarUrl: String?, // URL avatar

    @SerializedName("type")
    val type: String,

    @SerializedName("text")
    val text: String?
)
