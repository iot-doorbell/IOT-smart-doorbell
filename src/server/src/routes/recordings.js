const express = require('express');
const router = express.Router();
const recordingController = require('../controllers/recordingController');

router.get('/', recordingController.getRecordings);
router.post('/webhook', recordingController.handleWebhook);

module.exports = router;