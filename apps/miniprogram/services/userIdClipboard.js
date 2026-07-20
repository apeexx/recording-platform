const feedback = require('./feedback.js')

function copyUserId(userId) {
  const value = String(userId || '').trim()
  if (!value) return feedback.error('复制失败，请重试')
  wx.setClipboardData({
    data: value,
    success: () => feedback.info('用户 ID 已复制'),
    fail: () => feedback.error('复制失败，请重试')
  })
}

module.exports = { copyUserId }
