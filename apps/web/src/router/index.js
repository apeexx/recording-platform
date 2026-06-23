import { createRouter, createWebHistory } from 'vue-router'
import { adminRoutes } from './adminRoutes.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/admin/dashboard'
    },
    adminRoutes
  ]
})

export default router
