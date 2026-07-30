import { httpRequest } from './httpClient.js'
import { queryString } from './apiUtils.js'
export const reportApi = {
  dashboard() { return httpRequest('/api/reports/dashboard') },
  tasks(params = {}) { return httpRequest(`/api/reports/tasks${queryString(params)}`) },
  taskCollectors(taskId, params = {}) { return httpRequest(`/api/reports/tasks/${encodeURIComponent(taskId)}/collectors${queryString(params)}`) },
  collectors(params = {}) { return httpRequest(`/api/reports/collectors${queryString(params)}`) },
  collectorTask(collectorId, taskId, params = {}) {
    return httpRequest(`/api/reports/collectors/${encodeURIComponent(collectorId)}/tasks/${encodeURIComponent(taskId)}${queryString(params)}`)
  },
  collectorTaskSubmissions(collectorId, taskId, params = {}) {
    return httpRequest(`/api/reports/collectors/${encodeURIComponent(collectorId)}/tasks/${encodeURIComponent(taskId)}/submissions${queryString(params)}`)
  },
  me(params = {}) { return httpRequest(`/api/reports/me${queryString(params)}`) },
  submissions(params = {}) { return httpRequest(`/api/reports/me/submissions${queryString(params)}`) },
  operations(params = {}) { return httpRequest(`/api/operations${queryString(params)}`) },
  itemOperations(itemId, params = {}) { return httpRequest(`/api/task-items/${encodeURIComponent(itemId)}/operations${queryString(params)}`) }
}
