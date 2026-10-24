<script setup lang="ts">
import { computed, ref } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import { stockPriceApi, type DailyCloseResponse, type StockPrice } from '@/services/stockPriceApi'

const activeTab = ref<'current' | 'daily'>('current')
const symbol = ref('')
const loading = ref(false)
const errorMessage = ref('')
const price = ref<StockPrice | null>(null)
const dailySymbol = ref('')
const from = ref('')
const to = ref('')
const dailyLoading = ref(false)
const dailyError = ref('')
const dailyResult = ref<DailyCloseResponse | null>(null)

function formatPrice(value: string | undefined) {
  if (!value) return ''
  const match = /^(-?)(\d+)(\.\d+)?$/.exec(value)
  if (!match) return value
  return `${match[1]}${match[2]?.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}${match[3] ?? ''}`
}

const formattedPrice = computed(() => formatPrice(price.value?.lastPrice))

const formattedTime = computed(() => {
  const value = price.value?.timestamp
  if (!value) return '정보 없음'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '정보 없음' : date.toLocaleString()
})

async function search() {
  if (loading.value) return
  const code = symbol.value.trim()
  price.value = null
  errorMessage.value = ''
  if (!code || !/^[A-Za-z0-9.\-]+$/.test(code)) {
    errorMessage.value = '종목코드는 영문, 숫자, 점, 하이픈만 입력해 주세요.'
    return
  }
  loading.value = true
  try {
    price.value = await stockPriceApi.getPrice(code)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '시세 조회에 실패했습니다.'
  } finally {
    loading.value = false
  }
}

async function searchDaily() {
  if (dailyLoading.value) return
  dailyResult.value = null
  dailyError.value = ''
  const code = dailySymbol.value.trim()
  if (!code || !/^[A-Za-z0-9.\-]+$/.test(code)) {
    dailyError.value = '종목코드는 영문, 숫자, 점, 하이픈만 입력해 주세요.'
    return
  }
  if (!from.value || !to.value || from.value > to.value) {
    dailyError.value = '시작일과 종료일을 올바른 순서로 입력해 주세요.'
    return
  }
  dailyLoading.value = true
  try {
    dailyResult.value = await stockPriceApi.getDailyCloses(code, from.value, to.value)
  } catch (error) {
    dailyError.value = error instanceof Error ? error.message : '종가 조회에 실패했습니다.'
  } finally {
    dailyLoading.value = false
  }
}
</script>

<template>
  <section class="stock-price-page">
    <header class="stock-price-heading">
      <div>
        <h1>시세 조회</h1>
        <p>토스증권의 현재가와 기간별 종가를 조회합니다.</p>
      </div>
    </header>

    <div class="app-tabs stock-price-tabs" role="tablist" aria-label="시세 조회 종류">
      <button
        id="current-price-tab"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'current'"
        aria-controls="current-price-panel"
        @click="activeTab = 'current'"
      >
        현재가 조회
      </button>
      <button
        id="daily-price-tab"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'daily'"
        aria-controls="daily-price-panel"
        @click="activeTab = 'daily'"
      >
        기간별 종가 조회
      </button>
    </div>

    <div
      v-show="activeTab === 'current'"
      id="current-price-panel"
      class="stock-price-panel app-surface-panel"
      role="tabpanel"
      aria-labelledby="current-price-tab"
    >
      <form class="stock-price-form" @submit.prevent="search">
        <label for="price-symbol">종목코드</label>
        <div class="stock-price-form-row">
          <input
            id="price-symbol"
            v-model="symbol"
            class="app-text-input"
            type="text"
            placeholder="예: 005930 또는 AAPL"
            autocomplete="off"
            :disabled="loading"
            aria-describedby="price-symbol-help"
          />
          <button class="app-primary-button" type="submit" :disabled="loading">
            {{ loading ? '조회 중...' : '조회' }}
          </button>
        </div>
        <p id="price-symbol-help" class="stock-price-help">
          국내 종목코드 또는 해외 심볼 한 개를 입력하세요.
        </p>
      </form>

      <p v-if="errorMessage" class="app-error-message" role="alert">{{ errorMessage }}</p>
      <p v-if="loading" class="stock-price-state" role="status">현재가를 조회하고 있습니다.</p>

      <div v-if="price" class="stock-price-result" aria-live="polite">
        <div class="stock-price-result-main">
          <span class="stock-price-symbol">{{ price.symbol }}</span>
          <strong
            >{{ formattedPrice }} <small>{{ price.currency }}</small></strong
          >
        </div>
        <div class="stock-price-result-meta">
          <span>데이터 시각</span>
          <time v-if="price.timestamp" :datetime="price.timestamp">{{ formattedTime }}</time>
          <span v-else>정보 없음</span>
        </div>
      </div>
    </div>

    <div
      v-show="activeTab === 'daily'"
      id="daily-price-panel"
      class="stock-price-panel app-surface-panel"
      role="tabpanel"
      aria-labelledby="daily-price-tab"
    >
      <form class="stock-price-form" @submit.prevent="searchDaily">
        <label for="daily-price-symbol">종목코드</label>
        <input
          id="daily-price-symbol"
          v-model="dailySymbol"
          class="app-text-input"
          type="text"
          placeholder="예: 005930 또는 AAPL"
          autocomplete="off"
          :disabled="dailyLoading"
        />
        <div class="stock-price-dates">
          <div>
            <label for="daily-price-from">시작일</label>
            <input
              id="daily-price-from"
              v-model="from"
              class="app-text-input"
              type="date"
              :disabled="dailyLoading"
              required
            />
          </div>
          <div>
            <label for="daily-price-to">종료일</label>
            <input
              id="daily-price-to"
              v-model="to"
              class="app-text-input"
              type="date"
              :disabled="dailyLoading"
              required
            />
          </div>
        </div>
        <button class="app-primary-button" type="submit" :disabled="dailyLoading">
          {{ dailyLoading ? '조회 중...' : '종가 조회' }}
        </button>
      </form>

      <p v-if="dailyError" class="app-error-message" role="alert">{{ dailyError }}</p>
      <p v-if="dailyLoading" class="stock-price-state" role="status">
        기간별 종가를 조회하고 있습니다.
      </p>
      <div v-if="dailyResult && !dailyLoading" class="stock-price-daily-result" aria-live="polite">
        <h2>{{ dailyResult.symbol }} 종가</h2>
        <p v-if="dailyResult.prices.length === 0" class="stock-price-state">
          선택한 기간에 거래일 종가가 없습니다.
        </p>
        <DataTable v-else :value="dailyResult.prices" class="account-style-table" stripedRows>
          <Column field="date" header="거래일" />
          <Column header="종가">
            <template #body="{ data }">{{ formatPrice(data.closePrice) }}</template>
          </Column>
          <Column field="currency" header="통화" />
        </DataTable>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stock-price-page {
  max-width: 860px;
  margin: 0 auto;
}
.stock-price-heading {
  margin-bottom: 20px;
}
.stock-price-heading h1 {
  margin: 0;
  font-size: 25px;
}
.stock-price-heading p,
.stock-price-help,
.stock-price-state {
  color: var(--app-text-muted);
}
.stock-price-heading p {
  margin: 6px 0 0;
}
.stock-price-panel {
  padding: 24px;
}
.stock-price-tabs {
  margin-bottom: 16px;
}
.stock-price-form {
  display: grid;
  gap: 9px;
}
.stock-price-form label {
  font-weight: 700;
}
.stock-price-form-row {
  display: flex;
  gap: 10px;
}
.stock-price-form-row input {
  min-width: 0;
  flex: 1;
}
.stock-price-dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.stock-price-dates > div {
  display: grid;
  gap: 9px;
}
.stock-price-daily-result {
  margin-top: 28px;
}
.stock-price-daily-result h2 {
  margin: 0 0 14px;
  font-size: 18px;
}
.stock-price-help {
  margin: 0;
  font-size: 12px;
}
.stock-price-state {
  margin: 24px 0 0;
}
.stock-price-result {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--app-border);
}
.stock-price-result-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.stock-price-symbol {
  font-size: 18px;
  font-weight: 700;
}
.stock-price-result-main strong {
  color: var(--app-text);
  font-size: 28px;
  font-variant-numeric: tabular-nums;
}
.stock-price-result-main small {
  color: var(--app-text-muted);
  font-size: 14px;
}
.stock-price-result-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  color: var(--app-text-muted);
}
@media (max-width: 480px) {
  .stock-price-form-row,
  .stock-price-result-main {
    flex-direction: column;
    align-items: stretch;
  }
  .stock-price-dates {
    grid-template-columns: 1fr;
  }
}
</style>
