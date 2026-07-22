const PAGE_SIZE = 20

const pageCount = total => Math.max(1, Math.ceil(Math.max(Number(total) || 0, 0) / PAGE_SIZE))
const clampPage = (page, total) => Math.min(Math.max(Number(page) || 0, 0), pageCount(total) - 1)
const canPrevious = page => (Number(page) || 0) > 0
const canNext = (page, total) => (Number(page) || 0) + 1 < pageCount(total)

module.exports = { PAGE_SIZE, pageCount, clampPage, canPrevious, canNext }
