require('dotenv').config();
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fileUpload = require('express-fileupload');
const path = require('path');
const config = require('./config');
const {initMqttClient} = require('./services/mqttService');

// Routes
const authRoutes = require('./routes/auth');
const recordingRoutes = require('./routes/recordings');
const tokenRoutes = require('./routes/token');

const app = express();

// Middlewares
app.use(cors());
app.use(bodyParser.json());
app.use(fileUpload());

// Static files serving
app.use('/records', express.static(path.join(__dirname, 'records'), {
    maxAge: '1d',
    setHeaders: (res) => {
        res.setHeader('Accept-Ranges', 'bytes');
        res.setHeader('Cache-Control', 'public, max-age=86400');
    }
}));
app.use('/images', express.static(path.join(__dirname, 'images'), {maxAge: '1d'}));

// Routes
app.use('/auth', authRoutes);
app.use('/recordings', recordingRoutes);
app.use('/token', tokenRoutes);

// Initialize MQTT
initMqttClient();

app.listen(config.port, () => {
    console.log(`Server running on http://localhost:${config.port}`);
});