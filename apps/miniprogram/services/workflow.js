function createWorkflow({ operationId, submit }) {
  return {
    async submitWithRetry(payload) {
      const stable = operationId()
      const request = { ...payload, operationId: stable }
      try { return await submit(request) } catch (error) {
        if (!error.network) throw error
        return submit(request)
      }
    }
  }
}

function shouldAutoClaim(state) {
  return !!(state.enabled && state.submitted && state.grantActive && state.taskRunning && !state.poolEmpty)
}

module.exports = { createWorkflow, shouldAutoClaim }
