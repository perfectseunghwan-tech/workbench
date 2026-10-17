<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import DataTable from 'primevue/datatable'
import type { DataTablePageEvent, DataTableSortEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import { stockTradeApi, type StockTrade } from '../../services/stockTradeApi'
const props = defineProps<{ date: string }>()
const rows = ref<StockTrade[]>([])
const loading = ref(false)
const error = ref('')
const page = ref(0)
const total = ref(0)
const tradeType = ref('')
type TradeSortField =
  | 'tradeDate'
  | 'tradeType'
  | 'stockName'
  | 'accountName'
  | 'executionQuantity'
  | 'executionPrice'
  | 'executionAmount'
  | 'purchasePrice'
  | 'realizedProfitLoss'
  | 'tradeMemo'
const sortField = ref<TradeSortField>('tradeDate')
const sortOrder = ref<1 | -1>(-1)
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
      sortField: sortField.value,
      sortDirection: sortOrder.value === 1 ? 'asc' : 'desc',
    })
    if (tradeType.value) p.set('tradeType', tradeType.value)
    const r = await stockTradeApi.getTrades(p)
    rows.value = r.content
    total.value = r.totalElements
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
async function onPage(event: DataTablePageEvent) {
  page.value = event.page
  await load()
}
async function onSort(event: DataTableSortEvent) {
  const fields: TradeSortField[] = [
    'tradeDate',
    'tradeType',
    'stockName',
    'accountName',
    'executionQuantity',
    'executionPrice',
    'executionAmount',
    'purchasePrice',
    'realizedProfitLoss',
    'tradeMemo',
  ]
  if (typeof event.sortField !== 'string' || !fields.includes(event.sortField as TradeSortField))
    return
  sortField.value = event.sortField as TradeSortField
  sortOrder.value = event.sortOrder === 1 ? 1 : -1
  page.value = 0
  await load()
}
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
    <div v-else class="journal-table-wrap">
      <DataTable
        class="journal-trade-table"
        :value="rows"
        data-key="tradeId"
        :loading="loading"
        paginator
        lazy
        :rows="20"
        :first="page * 20"
        :total-records="total"
        :sort-field="sortField"
        :sort-order="sortOrder"
        scrollable
        table-style="min-width: 1120px"
        @page="onPage"
        @sort="onSort"
      >
        <Column field="tradeDate" header="거래일" sortable />
        <Column field="tradeType" header="구분" sortable
          ><template #body="{ data }"
            ><span :class="['trade-badge', data.tradeType.toLowerCase()]">{{
              data.tradeType === 'BUY' ? '매수' : '매도'
            }}</span></template
          ></Column
        >
        <Column field="stockName" header="종목" sortable />
        <Column field="accountName" header="증권사·계좌" sortable
          ><template #body="{ data }"
            >{{ data.brokerageName }} · {{ data.accountName }}</template
          ></Column
        >
        <Column field="executionQuantity" header="체결수량" sortable
          ><template #body="{ data }"
            ><span class="number">{{ number(data.executionQuantity) }}</span></template
          ></Column
        >
        <Column field="executionPrice" header="체결단가" sortable
          ><template #body="{ data }"
            ><span class="number">{{ number(data.executionPrice) }}</span></template
          ></Column
        >
        <Column field="executionAmount" header="체결금액" sortable
          ><template #body="{ data }"
            ><span class="number">{{ number(data.executionAmount) }}</span></template
          ></Column
        >
        <Column field="purchasePrice" header="매입단가" sortable
          ><template #body="{ data }"
            ><span class="number">{{ number(data.purchasePrice) }}</span></template
          ></Column
        >
        <Column field="realizedProfitLoss" header="실현손익" sortable
          ><template #body="{ data }"
            ><span class="number">{{ number(data.realizedProfitLoss) }}</span></template
          ></Column
        >
        <Column field="tradeMemo" header="거래메모" sortable
          ><template #body="{ data }">{{ data.tradeMemo || '—' }}</template></Column
        >
        <template #empty><div class="journal-empty">해당 날짜의 거래가 없습니다.</div></template>
      </DataTable>
    </div>
  </section>
</template>
