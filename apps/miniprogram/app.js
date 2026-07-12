const api = require('./services/api.js')
const { createSessionService } = require('./services/session.js')

App({
  globalData: { api, session: null },
  onLaunch() { this.globalData.session = createSessionService({ wx, api }) }
})
