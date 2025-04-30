const fs = require('fs');
const path = require('path');
const axios = require('axios');

async function ensureDir(dirPath) {
    if (!fs.existsSync(dirPath)) {
        await fs.promises.mkdir(dirPath, {recursive: true});
    }
}

async function downloadFile(url, outputPath) {
    const writer = fs.createWriteStream(outputPath);
    const response = await axios({
        url,
        method: 'GET',
        responseType: 'stream'
    });

    response.data.pipe(writer);

    return new Promise((resolve, reject) => {
        writer.on('finish', () => resolve(outputPath));
        writer.on('error', reject);
    });
}

async function cleanupTempFiles(dirPath, maxAge = 3600000) { // 1 hour
    const files = await fs.promises.readdir(dirPath);
    const now = Date.now();

    for (const file of files) {
        if (file.startsWith('temp_')) {
            const filePath = path.join(dirPath, file);
            const stats = await fs.promises.stat(filePath);
            if (now - stats.mtime.getTime() > maxAge) {
                await fs.promises.unlink(filePath);
            }
        }
    }
}

module.exports = {
    ensureDir,
    downloadFile,
    cleanupTempFiles
};