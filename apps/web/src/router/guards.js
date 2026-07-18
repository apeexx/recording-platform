export function homeForRole(role) {
  return role === 'REVIEWER' ? '/admin/review' : '/admin/dashboard'
}

export function createAdminRouteGuard(session) {
  return async (to) => {
    try {
      await session.initialize()
    } catch {
      if (to.meta.public) return true
      return { name: 'login', query: { reason: 'service-unavailable' } }
    }
    const currentUser = session.user.value

    if (!currentUser && to.meta.requiresAuth) return { name: 'login' }
    if (currentUser && to.name === 'login') return homeForRole(currentUser.role)
    if (!currentUser) return true

    if (currentUser.firstPasswordChangeRequired && to.name !== 'first-password') {
      return { name: 'first-password' }
    }
    if (!currentUser.firstPasswordChangeRequired && to.name === 'first-password') {
      return homeForRole(currentUser.role)
    }
    if (to.meta.roles && !to.meta.roles.includes(currentUser.role)) {
      return homeForRole(currentUser.role)
    }
    return true
  }
}
