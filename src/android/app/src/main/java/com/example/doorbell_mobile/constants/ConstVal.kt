package com.example.doorbell_mobile.constants

object ConstVal {
    const val DOORBELL_URL = " https://serverpi.loca.lt"
    const val WS_URL_SIGNAL = "wss://serverpi.loca.lt/signal"
    const val WS_URL_AUDIO = "wss://serverpi.loca.lt/text"
    const val LOGIN_ENDPOINT = "auth/login"
    const val MJEG_ENDPOINT = "video_feed"
    const val STOP_STREAM_ENDPOINT = "stop_feed"
}

// doorbell register:
// {"type":"register","role":"doorbell"}
// doorbell send message:
// {"type":"doorbell_to_server","status":"calling"}