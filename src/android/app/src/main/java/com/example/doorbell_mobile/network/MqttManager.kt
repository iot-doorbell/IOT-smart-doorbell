package com.example.doorbell_mobile.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import com.example.doorbell_mobile.constants.ConstVal
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object MqttManager {
    private const val TAG = "MqttManager"
    // Fixed client ID to ensure only one connection
    private const val CLIENT_ID = "AndroidMobile_doorbell_123"

    @SuppressLint("StaticFieldLeak")
    private var mqttClient: MqttAndroidClient? = null
    private var isConnected = false

    // Thread-safe list for multiple listeners
    private val messageListeners = CopyOnWriteArrayList<(JSONObject) -> Unit>()

    private var applicationContext: Context? = null

    // Reconnection parameters
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private val reconnectAttempts = AtomicInteger(0)
    private const val MAX_RECONNECT_ATTEMPTS = 5
    private const val RECONNECT_DELAY_MS = 5000L // 5 seconds

    // Timestamp formatter for logs
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Initialize MQTT connection
     */
    fun initMqtt(context: Context, onConnected: () -> Unit = {}, onError: (String) -> Unit = {}) {
        applicationContext = context.applicationContext

        // If already connected, just invoke the callback
        if (mqttClient != null && isConnected) {
            Timber.tag(TAG).d("MQTT already connected. Skipping connection.")
            onConnected()
            return
        }

        // Otherwise establish a new connection
        connectMqtt(context, onConnected, onError)
    }

    /**
     * Create and establish MQTT connection
     */
    private fun connectMqtt(context: Context, onConnected: () -> Unit = {}, onError: (String) -> Unit = {}) {
        Timber.tag(TAG).d("Initializing MQTT connection to: ${ConstVal.MQTT_URL}")

        try {
            // Create client with fixed CLIENT_ID
            mqttClient = MqttAndroidClient(
                context.applicationContext,
                ConstVal.MQTT_URL,
                CLIENT_ID
            )

            // Set up MQTT callbacks
            mqttClient?.setCallback(createMqttCallback(onError))

            // Connection options
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = ConstVal.MQTT_USERNAME
                password = ConstVal.MQTT_PASSWORD.toCharArray()
                connectionTimeout = 30 // seconds
                keepAliveInterval = 60 // seconds
                isAutomaticReconnect = true
            }

            // Connect and setup topic subscription on success
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Timber.tag(TAG).d("MQTT connection successful")
                    isConnected = true
                    reconnectAttempts.set(0)

                    // Subscribe to doorbell topic
                    subscribeToTopic(onConnected, onError)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val errorMsg = exception?.message ?: "Unknown connection error"
                    Timber.tag(TAG).e("MQTT connection failed: $errorMsg")
                    isConnected = false

                    // Schedule reconnection if under max attempts
                    scheduleReconnect(context, onConnected, onError)

                    onError(errorMsg)
                }
            })
        } catch (e: Exception) {
            val errorMsg = "Error initializing MQTT: ${e.message}"
            Timber.tag(TAG).e(errorMsg)
            onError(errorMsg)

            // Schedule reconnection
            scheduleReconnect(context, onConnected, onError)
        }
    }

    /**
     * Subscribe to the configured MQTT topic
     */
    private fun subscribeToTopic(onConnected: () -> Unit = {}, onError: (String) -> Unit = {}) {
        mqttClient?.subscribe(ConstVal.MQTT_TOPIC_SUBSCRIBE, ConstVal.MQTT_QOS, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Timber.tag(TAG).d("Subscribed to topic: ${ConstVal.MQTT_TOPIC_SUBSCRIBE}")
                onConnected()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                val errorMsg = "Failed to subscribe: ${exception?.message ?: "Unknown error"}"
                Timber.tag(TAG).e(errorMsg)
                onError(errorMsg)
            }
        })
    }

    /**
     * Schedule reconnection attempts with backoff
     */
    private fun scheduleReconnect(context: Context, onConnected: () -> Unit, onError: (String) -> Unit) {
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }

        if (reconnectAttempts.incrementAndGet() <= MAX_RECONNECT_ATTEMPTS) {
            val delay = RECONNECT_DELAY_MS * reconnectAttempts.get()
            Timber.tag(TAG).d("Scheduling reconnect attempt ${reconnectAttempts.get()}/$MAX_RECONNECT_ATTEMPTS in ${delay}ms")

            reconnectRunnable = Runnable {
                Timber.tag(TAG).d("Attempting MQTT reconnection...")
                connectMqtt(context, onConnected, onError)
            }

            reconnectHandler.postDelayed(reconnectRunnable!!, delay)
        } else {
            Timber.tag(TAG).e("Max reconnection attempts reached")
            onError("Failed to connect after $MAX_RECONNECT_ATTEMPTS attempts")
        }
    }

    /**
     * Add a message listener (doesn't replace existing ones)
     */
    fun addMessageListener(listener: (JSONObject) -> Unit) {
        if (!messageListeners.contains(listener)) {
            messageListeners.add(listener)
            Timber.tag(TAG).d("Message listener added, total listeners: ${messageListeners.size}")
        }
    }

    /**
     * Remove a specific message listener
     */
    fun removeMessageListener(listener: (JSONObject) -> Unit) {
        messageListeners.remove(listener)
        Timber.tag(TAG).d("Message listener removed, remaining listeners: ${messageListeners.size}")
    }

    /**
     * For backward compatibility - now it adds instead of replacing
     */
    fun setMessageListener(listener: (JSONObject) -> Unit) {
        // Simply add the listener rather than replacing all
        addMessageListener(listener)
        Timber.tag(TAG).d("Message listener set via legacy method")
    }

    /**
     * Process incoming message by notifying all listeners
     */
    private fun notifyListeners(jsonObject: JSONObject) {
        val status = jsonObject.optString("status", "")
        Timber.tag(TAG).d("Notifying ${messageListeners.size} listeners of message with status: $status")

        for (listener in messageListeners) {
            try {
                listener(jsonObject)
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error in message listener: ${e.message}")
            }
        }
    }

    /**
     * Create MQTT callback for connection and message handling
     */
    private fun createMqttCallback(onError: (String) -> Unit): MqttCallback {
        return object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                val reason = cause?.message ?: "Unknown reason"
                Timber.tag(TAG).e("MQTT Connection lost: $reason")
                isConnected = false

                // Auto-reconnect will be handled by the client itself
                // But we'll reset our reconnection count to allow manual reconnection
                reconnectAttempts.set(0)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val timestamp = timestampFormat.format(Date())
                Timber.tag(TAG).d("[$timestamp] Message arrived on topic [$topic]")

                message?.let {
                    try {
                        val payload = String(it.payload)
                        Timber.tag(TAG).d("Message content: $payload")

                        val jsonObject = JSONObject(payload)
                        // Notify all registered listeners
                        notifyListeners(jsonObject)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e("Error parsing message: ${e.message}")
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Timber.tag(TAG).d("Message delivery complete")
            }
        }
    }

    /**
     * Send message to the doorbell via MQTT
     */
    fun sendMessage(
        json: JSONObject,
        topic: String = ConstVal.MQTT_TOPIC_PUBLISH,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (mqttClient == null || !isConnected) {
            val error = "Cannot send message: MQTT not connected"
            Timber.tag(TAG).e(error)
            onError(error)
            return
        }

        try {
            val message = MqttMessage(json.toString().toByteArray())
            message.qos = ConstVal.MQTT_QOS
            message.isRetained = false

            Timber.tag(TAG).d("Sending message to $topic: $json")
            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Timber.tag(TAG).d("Message sent successfully")
                    onSuccess()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    val errorMsg = "Failed to send message: ${exception?.message ?: "Unknown error"}"
                    Timber.tag(TAG).e(errorMsg)
                    onError(errorMsg)
                }
            })
        } catch (e: Exception) {
            val errorMsg = "Error sending message: ${e.message}"
            Timber.tag(TAG).e(errorMsg)
            onError(errorMsg)
        }
    }

    /**
     * Check connection status
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Disconnect MQTT client
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Timber.tag(TAG).d("MQTT disconnected successfully")
                    isConnected = false
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Timber.tag(TAG).e("MQTT disconnect failed: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error disconnecting MQTT: ${e.message}")
        }
    }

    /**
     * Clean up resources - call this in app termination
     */
    fun cleanup() {
        disconnect()
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }
        reconnectRunnable = null
        messageListeners.clear()
        mqttClient?.close()
        mqttClient = null
        applicationContext = null
    }
}