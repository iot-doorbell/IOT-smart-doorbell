const pool = require('../database');
const videoService = require('../services/videoService');
const {downloadFile} = require('../utils/fileHelper');
const path = require('path');
const config = require('../config');

async function getRecordings(req, res) {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 10;
        const offset = (page - 1) * limit;

        const [recordings, count] = await Promise.all([
            pool.query(`
                SELECT id, status, start_time, end_time, video_url, img_url
                FROM recordings
                ORDER BY start_time DESC
                LIMIT $1 OFFSET $2
            `, [limit, offset]),
            pool.query('SELECT COUNT(*) FROM recordings')
        ]);

        res.json({
            recordings: recordings.rows,
            totalItems: parseInt(count.rows[0].count),
            currentPage: page
        });
    } catch (error) {
        console.error('Database error:', error);
        res.status(500).send('Database error');
    }
}

async function handleWebhook(req, res) {
    const {downloadUrl, conferenceId, startTime, endTime} = req.body;

    if (!downloadUrl || !conferenceId || !startTime || !endTime) {
        return res.status(400).send('Missing required fields');
    }

    try {
        const tempPath = path.join(config.paths.records, `temp_${Date.now()}.mp4`);
        await downloadFile(downloadUrl, tempPath);

        const result = await videoService.processVideo(tempPath, startTime, endTime, conferenceId);
        await saveToDatabase(result, conferenceId);

        res.sendStatus(200);
    } catch (error) {
        console.error('Webhook error:', error);
        res.status(500).send('Processing failed');
    }
}

async function handleUpload(req, res) {
    if (!req.files?.callRecord) {
        return res.status(400).send('No file uploaded');
    }

    try {
        const file = req.files.callRecord;
        const tempPath = path.join(config.paths.records, `temp_${Date.now()}.mp4`);
        await file.mv(tempPath);

        const result = await videoService.processVideo(
            tempPath,
            new Date().toISOString(),
            new Date().toISOString(),
            'direct-upload'
        );

        res.json(result);
    } catch (error) {
        console.error('Upload error:', error);
        res.status(500).send('Upload failed');
    }
}

async function saveToDatabase(result, conferenceId) {
    await pool.query(
        'INSERT INTO recordings (video_url, img_url, conference_id) VALUES ($1, $2, $3)',
        [result.videoUrl, result.imageUrl, conferenceId]
    );
}

module.exports = {
    getRecordings,
    handleWebhook,
    handleUpload
};