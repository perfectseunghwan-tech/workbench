<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import type { DataTableSortEvent } from 'primevue/datatable'
import Column from 'primevue/column'
import {
  investmentHorizons,
  investmentStrategyApi,
  strategyScopes,
  strategyTypes,
  type StrategySummary,
} from '../services/investmentStrategyApi'
const router = useRouter(),
  rows = ref<StrategySummary[]>([]),
  loading = ref(false),
  error = ref(''),
  keyword = ref(''),
  scope = ref(''),
  strategyType = ref(''),
  investmentHorizon = ref(''),
  sort = ref<StrategySortField>('latestActivityAt'),
  sortOrder = ref<1 | -1>(-1),
  page = ref(0),
  total = ref(0)
const strategyTypeName = (code: string) => strategyTypes.find((x) => x[0] === code)?.[1] || code
const investmentHorizonName = (code: string) =>
  investmentHorizons.find((x) => x[0] === code)?.[1] || code
const scopeName = (scope: StrategySummary['strategyScope']) =>
  strategyScopes.find((item) => item.code === scope)?.name || scope
const availableTypes = computed(() =>
  scope.value
    ? (strategyScopes.find((item) => item.code === scope.value)?.types ?? [])
    : strategyTypes,
)
function changeScope() {
  if (!availableTypes.value.some((type) => type[0] === strategyType.value)) {
    strategyType.value = ''
  }
}
type StrategySortField =
  | 'latestActivityAt'
  | 'createdAt'
  | 'title'
  | 'strategyScope'
  | 'strategyType'
  | 'investmentHorizon'
const date = (v: string) => new Date(v).toLocaleDateString('ko-KR')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const p = new URLSearchParams({
      page: String(page.value),
      size: '20',
      sortField: sort.value,
      sortDirection: sortOrder.value === 1 ? 'asc' : 'desc',
    })
    if (keyword.value.trim()) p.set('keyword', keyword.value.trim())
    if (scope.value) p.set('strategyScope', scope.value)
    if (strategyType.value) p.set('strategyType', strategyType.value)
    if (investmentHorizon.value) p.set('investmentHorizon', investmentHorizon.value)
    const r = await investmentStrategyApi.list(p)
    rows.value = r.content
    total.value = r.totalElements
  } catch (e) {
    error.value = e instanceof Error ? e.message : '목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 0
  load()
}
function reset() {
  keyword.value = ''
  scope.value = ''
  strategyType.value = ''
  investmentHorizon.value = ''
  sort.value = 'latestActivityAt'
  sortOrder.value = -1
  search()
}
function onSortPresetChange() {
  sortOrder.value = sort.value === 'title' ? 1 : -1
  search()
}
async function onPage(event: { page: number }) {
  page.value = event.page
  await load()
}
async function onSort(event: DataTableSortEvent) {
  const fields: StrategySortField[] = [
    'latestActivityAt',
    'createdAt',
    'title',
    'strategyScope',
    'strategyType',
    'investmentHorizon',
  ]
  if (typeof event.sortField !== 'string' || !fields.includes(event.sortField as StrategySortField))
    return
  sort.value = event.sortField as StrategySortField
  sortOrder.value = event.sortOrder === 1 ? 1 : -1
  page.value = 0
  await load()
}
async function remove(row: StrategySummary) {
  if (!confirm(`'${row.title}' 전략을 삭제하시겠습니까?`)) return
  try {
    await investmentStrategyApi.remove(row.strategyId)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}
onMounted(load)
</script>
<template>
  <div class="strategy-page">
    <header class="strategy-head">
      <div>
        <p>INVESTMENT STRATEGY</p>
        <h1>투자전략 관리</h1>
        <span>투자 원칙과 분석, 실행 기록을 한곳에서 관리합니다.</span>
      </div>
      <button class="primary" @click="router.push('/strategies/new')">
        <i class="pi pi-plus" /> 전략 등록
      </button>
    </header>
    <section class="strategy-card filters account-style-search">
      <input
        v-model="keyword"
        placeholder="전략명, 본문, 종목명 검색"
        @keyup.enter="search"
      /><select v-model="scope" @change="changeScope">
        <option value="">전체 적용 범위</option>
        <option v-for="item in strategyScopes" :key="item.code" :value="item.code">
          {{ item.name }}
        </option></select
      ><select v-model="strategyType">
        <option value="">전체 전략 유형</option>
        <option v-for="type in availableTypes" :key="type[0]" :value="type[0]">
          {{ type[1] }}
        </option></select
      ><select v-model="investmentHorizon">
        <option value="">전체 투자 기간</option>
        <option v-for="horizon in investmentHorizons" :key="horizon[0]" :value="horizon[0]">
          {{ horizon[1] }}
        </option></select
      ><select v-model="sort" @change="onSortPresetChange">
        <option value="latestActivityAt">최근 활동순</option>
        <option value="createdAt">최초 작성순</option>
        <option value="title">전략명순</option>
        <option value="strategyScope">적용 범위순</option>
        <option value="strategyType">전략 유형순</option>
        <option value="investmentHorizon">투자 기간순</option></select
      ><button class="primary" @click="search">조회</button
      ><button class="secondary" @click="reset">초기화</button>
    </section>
    <p v-if="error" class="strategy-alert">{{ error }}</p>
    <section class="strategy-card table-card">
      <DataTable
        class="account-style-table"
        :value="rows"
        :loading="loading"
        data-key="strategyId"
        paginator
        :lazy="true"
        :rows="20"
        :total-records="total"
        :first="page * 20"
        :sort-field="sort"
        :sort-order="sortOrder"
        @page="onPage"
        @sort="onSort"
        @row-click="router.push(`/strategies/${$event.data.strategyId}`)"
        table-style="min-width: 980px"
        ><Column field="title" header="전략" sortable
          ><template #body="{ data }"
            ><button class="title-link" @click.stop="router.push(`/strategies/${data.strategyId}`)">
              {{ data.title }}</button
            ><small>V{{ data.versionNo }} · {{ scopeName(data.strategyScope) }}</small></template
          ></Column
        ><Column field="strategyType" header="전략 유형" sortable
          ><template #body="{ data }">{{
            data.strategyType ? strategyTypeName(data.strategyType) : '—'
          }}</template></Column
        ><Column field="investmentHorizon" header="투자 기간" sortable
          ><template #body="{ data }">{{
            data.investmentHorizon ? investmentHorizonName(data.investmentHorizon) : '—'
          }}</template></Column
        ><Column header="관련 종목"
          ><template #body="{ data }">{{
            data.stocks.map((s: StrategySummary['stocks'][number]) => s.stockName).join(', ') || '—'
          }}</template></Column
        ><Column field="latestActivityAt" header="최근 활동" sortable
          ><template #body="{ data }">{{ date(data.latestActivityAt) }}</template></Column
        ><Column header="관리"
          ><template #body="{ data }"
            ><div class="actions">
              <button
                class="secondary"
                @click.stop="router.push(`/strategies/${data.strategyId}/edit`)"
              >
                수정</button
              ><button class="danger" @click.stop="remove(data)">삭제</button>
            </div></template
          ></Column
        ><template #empty><div class="empty">등록된 투자전략이 없습니다.</div></template></DataTable
      >
    </section>
  </div>
</template>
<style scoped src="../assets/strategy.css"></style>
