import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'

const e = encodeURIComponent

export const batchOperationApi = {
  preview(selection) {
    return httpRequest('/api/batch-operation-jobs/preview', { method: 'POST', json: selection })
  },
  create(command) {
    return httpRequest('/api/batch-operation-jobs', { method: 'POST', json: command })
  },
  get(jobId) {
    return httpRequest(`/api/batch-operation-jobs/${e(jobId)}`)
  },
  recent(taskId, source) {
    return httpRequest(`/api/batch-operation-jobs${queryString({ taskId, source })}`)
  },
}
