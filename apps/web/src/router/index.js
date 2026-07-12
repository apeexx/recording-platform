import { createRouter, createWebHistory } from 'vue-router'
import { adminRoutes } from './adminRoutes.js'
import { useAdminSession, configureSessionReplacementNavigation } from '../composables/useAdminSession.js'
import { createAdminRouteGuard } from './guards.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/admin/dashboard'
    },
    { path: '/login', name: 'login', component: () => import('../pages/auth/AdminLoginPage.vue'), meta: { public: true } },
    { path: '/first-password', name: 'first-password', component: () => import('../pages/auth/FirstPasswordPage.vue'), meta: { requiresAuth: true } },
    adminRoutes
  ]
})

router.beforeEach(createAdminRouteGuard(useAdminSession()))
configureSessionReplacementNavigation(() => {
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { reason: 'session-replaced' } })
  }
})

export default router
