const ffmpeg = require('fluent-ffmpeg');
const path = require('path');
const fs = require('fs');
const config = require('../config');
const {ensureDir} = require("../utils/fileHelper");

class VideoService {
    async processVideo(inputPath, startTime, endTime, conferenceId) {
        const fileName = `${startTime}_${endTime}_${conferenceId}`;

        // ensure folder is exists
        await ensureDir(config.paths.records);
        await ensureDir(config.paths.images);

        const newVideoPath = path.join(config.paths.records, `${fileName}.mp4`);
        path.join(config.paths.images, `${fileName}.png`);
        await fs.promises.rename(inputPath, newVideoPath);
        await this.generateThumbnail(newVideoPath, fileName);

        return {
            videoUrl: `records/${fileName}.mp4`,
            imageUrl: `images/${fileName}.png`
        };
    }

    async generateThumbnail(videoPath, fileName) {
        return new Promise((resolve, reject) => {
            ffmpeg(videoPath)
                .screenshots({
                    count: 1,
                    filename: `${fileName}.png`,
                    folder: config.paths.images,
                    size: '320x240'
                })
                .on('end', resolve)
                .on('error', reject);
        });
    }
}

module.exports = new VideoService();