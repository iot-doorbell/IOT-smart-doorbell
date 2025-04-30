const jwt = require('jsonwebtoken');
const config = require('../config');

function generateToken(req, res) {
    const now = Math.floor(Date.now() / 1000);
    const token = jwt.sign({
        aud: 'jitsi',
        iss: 'chat',
        sub: config.jaas.appId,
        nbf: now,
        exp: now + 3600,
        context: {
            features: {
                livestreaming: true,
                'outbound-call': true,
                'sip-outbound-call': false,
                transcription: true,
                recording: true
            },
            user: {
                'hidden-from-recorder': false,
                moderator: true,
                name: config.user.name,
                id: config.user.id,
                avatar: '',
                email: config.user.email
            }
        }
    }, config.jaas.privateKey, {
        algorithm: 'RS256',
        keyid: config.jaas.keyId,
        type: 'JWT'
    });

    res.json({token});
}

module.exports = {
    generateToken
};