import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '../views/AppShell.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/StockTradeView.vue'),
          meta: { title: '거래 내역' },
        },
        {
          path: 'settings/accounts',
          name: 'account-settings',
          component: () => import('../views/AccountSettingsView.vue'),
          meta: { title: '증권사·계좌 관리' },
        },
        {
          path: 'connection',
          name: 'connection',
          component: () => import('../views/HomeView.vue'),
          meta: { title: '서버 연결 확인' },
        },
      ],
    },
  ],
})

export default router
