const config = require('../config');

async function login(req, res) {
    const {username, password} = req.body;

    if (username === config.user.name && password === config.user.password) {
        res.status(200).json({
            userId: config.user.id,
            userName: config.user.name,
            userEmail: config.user.email
        });
    } else {
        res.status(401).send({message: 'Invalid credentials'});
    }
}

module.exports = {
    login
};