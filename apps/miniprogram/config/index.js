let privateConfig = {}
try { privateConfig = require('./private.js') } catch (_) { privateConfig = {} }

module.exports = {
  apiBaseUrl: privateConfig.apiBaseUrl || 'http://127.0.0.1:8080'
}
