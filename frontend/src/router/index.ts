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
          path: 'balances',
          name: 'stock-balances',
          component: () => import('../views/StockBalanceView.vue'),
          meta: { title: '주식잔고' },
        },
        {
          path: 'strategies',
          name: 'investment-strategy-list',
          component: () => import('../views/InvestmentStrategyListView.vue'),
          meta: { title: '투자전략 관리' },
        },
        {
          path: 'strategies/new',
          name: 'investment-strategy-create',
          component: () => import('../views/InvestmentStrategyEditView.vue'),
          meta: { title: '투자전략 등록' },
        },
        {
          path: 'strategies/:strategyId',
          name: 'investment-strategy-detail',
          component: () => import('../views/InvestmentStrategyDetailView.vue'),
          meta: { title: '투자전략 상세' },
        },
        {
          path: 'strategies/:strategyId/edit',
          name: 'investment-strategy-edit',
          component: () => import('../views/InvestmentStrategyEditView.vue'),
          meta: { title: '투자전략 수정' },
        },
        {
          path: 'journals',
          name: 'investment-journal-list',
          component: () => import('../views/InvestmentJournalListView.vue'),
          meta: { title: '투자일지' },
        },
        {
          path: 'journals/new',
          name: 'investment-journal-create',
          component: () => import('../views/InvestmentJournalEditView.vue'),
          meta: { title: '투자일지 작성' },
        },
        {
          path: 'journals/:journalId',
          name: 'investment-journal-detail',
          component: () => import('../views/InvestmentJournalDetailView.vue'),
          meta: { title: '투자일지 상세' },
        },
        {
          path: 'journals/:journalId/edit',
          name: 'investment-journal-edit',
          component: () => import('../views/InvestmentJournalEditView.vue'),
          meta: { title: '투자일지 수정' },
        },
        {
          path: 'settings/accounts',
          name: 'account-settings',
          component: () => import('../views/AccountSettingsView.vue'),
          meta: { title: '증권사·계좌 관리' },
        },
        {
          path: 'settings/stocks',
          name: 'stock-settings',
          component: () => import('../views/StockSettingsView.vue'),
          meta: { title: '주식종목 관리' },
        },
        {
          path: 'connection',
          name: 'connection',
          component: () => import('../views/HomeView.vue'),
          meta: { title: '서버 연결 확인' },
        },
        {
          path: 'ai-chat',
          name: 'ai-chat',
          component: () => import('../views/AiChatView.vue'),
          meta: { title: 'AI 채팅' },
        },
        {
          path: 'ai-analyses',
          name: 'ai-analysis-list',
          component: () => import('../views/AiAnalysisView.vue'),
          meta: { title: 'AI 분석 관리' },
        },
        {
          path: 'ai-analyses/new',
          name: 'ai-analysis-new',
          component: () => import('../views/AiAnalysisView.vue'),
          meta: { title: 'AI 분석 등록' },
        },
        {
          path: 'ai-analyses/:analysisId',
          name: 'ai-analysis-detail',
          component: () => import('../views/AiAnalysisView.vue'),
          meta: { title: 'AI 분석 상세' },
        },
        {
          path: 'ai-analyses/:analysisId/edit',
          name: 'ai-analysis-edit',
          component: () => import('../views/AiAnalysisView.vue'),
          meta: { title: 'AI 분석 수정' },
        },
      ],
    },
  ],
})

export default router
