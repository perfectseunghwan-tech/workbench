<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { stockTradeApi, type StockTrade } from '../../services/stockTradeApi'
const props = defineProps<{ date: string }>()
const rows = ref<StockTrade[]>([])
const loading = ref(false)
const error = ref('')
const page = ref(0)
const totalPages = ref(0)
const tradeType = ref('')
const number = (v: number | string | null) =>
  v == null ? '—' : Number(v).toLocaleString('ko-KR', { maximumFractionDigits: 2 })
async function load() {
  if (!props.date) {
    rows.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const p = new URLSearchParams({
      dateFrom: props.date,
      dateTo: props.date,
      page: String(page.value),
      size: '20',
    })
    if (tradeType.value) p.set('tradeType', tradeType.value)
    const r = await stockTradeApi.getTrades(p)
    rows.value = r.content
    totalPages.value = r.totalPages
  } catch (e) {
    error.value = e instanceof Error ? e.message : '거래 내역을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
watch(
  () => props.date,
  () => {
    page.value = 0
    load()
  },
)
watch(tradeType, () => {
  page.value = 0
  load()
})
onMounted(load)
</script>
<template>
  <section class="journal-card trade-reference">
    <header>
      <div>
        <h2>당일 매수·매도 기록</h2>
        <p>일지 기준일의 거래를 읽기 전용으로 표시합니다.</p>
      </div>
      <select v-model="tradeType" aria-label="거래구분">
        <option value="">전체</option>
        <option value="BUY">매수</option>
        <option value="SELL">매도</option>
      </select>
    </header>
    <p v-if="!date" class="journal-empty">일지일자를 선택해 주세요.</p>
    <p v-else-if="error" class="journal-alert">{{ error }}</p>
    <p v-else-if="loading" class="journal-empty">거래 내역을 불러오는 중입니다.</p>
    <div v-else class="journal-table-wrap">
      <table>
        <thead>
          <tr>
            <th>구분</th>
            <th>종목</th>
            <th>증권사·계좌</th>
            <th>체결수량</th>
            <th>체결단가</th>
            <th>체결금액</th>
            <th>매입단가</th>
            <th>실현손익</th>
            <th>거래메모</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.tradeId">
            <td>
              <span :class="['trade-badge', r.tradeType.toLowerCase()]">{{
                r.tradeType === 'BUY' ? '매수' : '매도'
              }}</span>
            </td>
            <td>{{ r.stockName }}</td>
            <td>{{ r.brokerageName }} · {{ r.accountName }}</td>
            <td class="number">{{ number(r.executionQuantity) }}</td>
            <td class="number">{{ number(r.executionPrice) }}</td>
            <td class="number">{{ number(r.executionAmount) }}</td>
            <td class="number">{{ number(r.purchasePrice) }}</td>
            <td class="number">{{ number(r.realizedProfitLoss) }}</td>
            <td>{{ r.tradeMemo || '—' }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="9" class="journal-empty">해당 날짜의 거래가 없습니다.</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="totalPages > 1" class="journal-pager">
      <button
        :disabled="page === 0"
        @click="
          page--
          load()
        "
      >
        이전</button
      ><span>{{ page + 1 }} / {{ totalPages }}</span
      ><button
        :disabled="page + 1 >= totalPages"
        @click="
          page++
          load()
        "
      >
        다음
      </button>
    </div>
  </section>
</template>
