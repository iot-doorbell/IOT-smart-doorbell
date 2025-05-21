package com.example.doorbell_mobile.constants

object ConstVal {
    const val DOORBELL_URL = "https://internal-tadpole-thoroughly.ngrok-free.app"
    const val WS_URL_SIGNAL = "wss://internal-tadpole-thoroughly.ngrok-free.app/signal"
    const val WS_URL_AUDIO = "wss://internal-tadpole-thoroughly.ngrok-free.app/text"
    const val LOGIN_ENDPOINT = "auth/login"
    const val MJEG_ENDPOINT = "video_feed"
    const val STOP_STREAM_ENDPOINT = "stop_feed"
    const val MQTT_URL = "ssl://730247187d394eafac50bfb46350c7ec.s1.eu.hivemq.cloud:8883"
    const val MQTT_USERNAME = "doorbell"
    const val MQTT_PASSWORD = "Doorbell123"
    const val MQTT_TOPIC_SUBSCRIBE = "doorbell_to_app/cmd"
    const val MQTT_TOPIC_PUBLISH = "app_to_doorbell/cmd"
    const val MQTT_QOS = 1
    const val USERNAME = "dev405051"
    const val PASSWORD = "123456789"
    const val EMAIL = "dev405051@gmail.com"
}