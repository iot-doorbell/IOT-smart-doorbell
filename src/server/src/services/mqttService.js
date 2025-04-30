const mqtt = require('mqtt');
const config = require('../config');
const pool = require('../database');

let mqttClient;

async function handleDoorbellCall(data) {
    const {status, start_time} = data;
    try {
        await pool.query(
            'INSERT INTO recordings (status, start_time, end_time) VALUES ($1, $2, $2)',
            [status, start_time]
        );
        mqttClient.publish('app/call', JSON.stringify(data));
    } catch (error) {
        console.error('Database error:', error);
    }
}

async function handleDoorbellResponse(data) {
    const {status, end_time} = data;
    try {
        await pool.query(
            'UPDATE recordings SET end_time = $1, status = $2 WHERE status = $3',
            [end_time, status, 'calling']
        );
        mqttClient.publish('pi/response', JSON.stringify(data));
    } catch (error) {
        console.error('Database error:', error);
    }
}

async function handleMessage(topic, message) {
    try {
        const data = JSON.parse(message.toString());

        if (topic === 'doorbell/call') {
            await handleDoorbellCall(data);
        } else if (topic === 'doorbell/response') {
            await handleDoorbellResponse(data);
        }
    } catch (error) {
        console.error('MQTT handler error:', error);
    }
}

function initMqttClient() {
    mqttClient = mqtt.connect(`mqtts://${config.mqtt.host}:${config.mqtt.port}`, {
        username: config.mqtt.username,
        password: config.mqtt.password,
        rejectUnauthorized: false
    });

    mqttClient.on('connect', () => {
        console.log('MQTT Connected');
        mqttClient.subscribe(['doorbell/call', 'doorbell/response']);
    });

    mqttClient.on('message', handleMessage);

    return mqttClient;
}

function publish(topic, message) {
    if (mqttClient && mqttClient.connected) {
        mqttClient.publish(topic, JSON.stringify(message));
    }
}

module.exports = {
    initMqttClient,
    publish
};