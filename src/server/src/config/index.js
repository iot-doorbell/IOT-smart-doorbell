const path = require('path');
const fs = require('fs');

module.exports = {
    port: process.env.PORT || 3000,
    db: {
        url: process.env.DB_URL
    },
    mqtt: {
        host: process.env.MQTT_HOST,
        port: process.env.MQTT_PORT,
        username: process.env.MQTT_USERNAME,
        password: process.env.MQTT_PASSWORD
    },
    jaas: {
        appId: process.env.JAAS_APP_ID,
        keyId: process.env.JWT_KEY_ID,
        privateKey: fs.readFileSync(path.resolve(process.env.JWT_PRIVATE_KEY_PATH))
    },
    paths: {
        records: path.join(__dirname, '../records'),
        images: path.join(__dirname, '../images')
    },
    user: {
        id: 'auth0|680d85dddc80fadfd24d966a',
        name: 'dev405051',
        email: 'dev405051@gmail.com',
        password: '123456789'
    }
};