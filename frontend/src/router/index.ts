import { createRouter, createWebHistory } from 'vue-router'
import StockTradeView from '../views/StockTradeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: StockTradeView,
    },
    {
      path: '/settings/accounts',
      name: 'account-settings',
      component: () => import('../views/AccountSettingsView.vue'),
    },
    {
      path: '/connection',
      name: 'connection',
      component: () => import('../views/HomeView.vue'),
    },
  ],
})

export default router
