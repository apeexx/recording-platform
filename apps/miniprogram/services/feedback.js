function success(title) { wx.showToast({ title, icon: 'success' }) }
function error(title) { wx.showToast({ title: title || '操作失败', icon: 'none' }) }
function info(title) { wx.showToast({ title, icon: 'none' }) }
module.exports = { success, error, info }
