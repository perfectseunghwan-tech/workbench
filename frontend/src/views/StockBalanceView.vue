<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import {
  stockTradeApi,
  type Brokerage,
  type InvestmentAccount,
  type Stock,
  type StockTrade,
} from '@/services/stockTradeApi'
import {
  stockBalanceApi,
  type ApplicationGroup,
  type Balance,
  type BalancePayload,
  type SnapshotHeader,
} from '@/services/stockBalanceApi'

const route = useRoute()
const brokerages = ref<Brokerage[]>([])
const accounts = ref<InvestmentAccount[]>([])
const stocks = ref<Stock[]>([])
const selectedStock = ref<Stock | null>(null)
const showStockPicker = ref(false)
const stockPickerLoading = ref(false)
const stockPickerError = ref('')
const stockPickerFilters = ref({ keyword: '', marketCode: '' })
const brokerageId = ref<number | ''>('')
const accountId = ref<number | ''>('')
const balances = ref<Balance[]>([])
const pending = ref<StockTrade[]>([])
const pendingPage = ref(0)
const pendingTotal = ref(0)
const pendingLoading = ref(false)
const pendingError = ref('')
const pendingFilters = ref({ dateFrom: '', dateTo: '', keyword: '' })
const selectedIds = ref<number[]>([])
const groups = ref<ApplicationGroup[]>([])
const snapshots = ref<SnapshotHeader[]>([])
const selectedSnapshot = ref<{
  header: SnapshotHeader
  details: Balance[]
} | null>(null)
const error = ref('')
const notice = ref('')
const busy = ref(false)
const editing = ref(false)
const editingBalanceId = ref<number | null>(null)
const applying = ref(false)
const form = ref({
  accountId: '' as number | '',
  stockId: '' as number | '',
  balanceQuantity: '0',
  currentPrice: '',
  purchaseAmount: '',
  purchasePrice: '',
  evaluationAmount: '',
  evaluationProfitLoss: '',
  returnRate: '',
})

const selectedBrokerage = computed(
  () => brokerages.value.find((item) => item.brokerageId === brokerageId.value) ?? null,
)
const filteredAccounts = computed(() =>
  accounts.value.filter((a) => a.brokerageId === brokerageId.value),
)
const selectedAccount = computed(
  () => accounts.value.find((item) => item.accountId === accountId.value) ?? null,
)
const totals = computed(() => {
  const byCurrency = new Map<string, number>()
  for (const balance of balances.value) {
    if (balance.evaluationAmount !== null)
      byCurrency.set(
        balance.currencyCode,
        (byCurrency.get(balance.currencyCode) ?? 0) + Number(balance.evaluationAmount),
      )
  }
  return [...byCurrency.entries()]
})
const fmt = (value: number | string | null, digits = 2) =>
  value === null
    ? '—'
    : new Intl.NumberFormat('ko-KR', { maximumFractionDigits: digits }).format(Number(value))
const key = (g: ApplicationGroup) => `${g.accountId}:${g.stockId}`

function clearError() {
  error.value = ''
  notice.value = ''
}
async function loadBalances() {
  if (!accountId.value) {
    balances.value = []
    return
  }
  const params = new URLSearchParams()
  if (brokerageId.value) params.set('brokerageId', String(brokerageId.value))
  if (accountId.value) params.set('accountId', String(accountId.value))
  balances.value = await stockBalanceApi.list(params)
}
async function loadPending() {
  if (!accountId.value) {
    pending.value = []
    pendingTotal.value = 0
    pendingError.value = ''
    return
  }
  pendingLoading.value = true
  pendingError.value = ''
  const params = new URLSearchParams({
    balanceApplied: 'false',
    page: String(pendingPage.value),
    size: '100',
  })
  if (brokerageId.value) params.set('brokerageId', String(brokerageId.value))
  if (accountId.value) params.set('accountId', String(accountId.value))
  if (pendingFilters.value.dateFrom) params.set('dateFrom', pendingFilters.value.dateFrom)
  if (pendingFilters.value.dateTo) params.set('dateTo', pendingFilters.value.dateTo)
  if (pendingFilters.value.keyword.trim())
    params.set('keyword', pendingFilters.value.keyword.trim())
  try {
    const page = await stockTradeApi.getTrades(params)
    pending.value = page.content
    pendingTotal.value = page.totalElements
  } catch (e) {
    pending.value = []
    pendingTotal.value = 0
    pendingError.value = e instanceof Error ? e.message : '미반영 거래를 불러오지 못했습니다.'
  } finally {
    pendingLoading.value = false
  }
}
function changePendingPage(direction: number) {
  pendingPage.value += direction
  void loadPending()
}
function searchPending() {
  if (
    pendingFilters.value.dateFrom &&
    pendingFilters.value.dateTo &&
    pendingFilters.value.dateFrom > pendingFilters.value.dateTo
  ) {
    pendingError.value = '시작일은 종료일보다 늦을 수 없습니다.'
    return
  }
  pendingPage.value = 0
  selectedIds.value = []
  groups.value = []
  void loadPending()
}
async function loadSnapshots() {
  snapshots.value = accountId.value ? await stockBalanceApi.snapshots(accountId.value) : []
  selectedSnapshot.value = null
}
async function refresh() {
  clearError()
  try {
    await Promise.all([loadBalances(), loadPending(), loadSnapshots()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '자료를 불러오지 못했습니다.'
  }
}
function selectBrokerage(id: number) {
  brokerageId.value = id
  accountId.value = accounts.value.find((item) => item.brokerageId === id)?.accountId ?? ''
  pendingPage.value = 0
  pendingFilters.value = { dateFrom: '', dateTo: '', keyword: '' }
  selectedIds.value = []
  groups.value = []
  void refresh()
}
function selectAccount(id: number) {
  accountId.value = id
  pendingPage.value = 0
  pendingFilters.value = { dateFrom: '', dateTo: '', keyword: '' }
  selectedIds.value = []
  groups.value = []
  void refresh()
}
function openForm(balance?: Balance) {
  error.value = ''
  notice.value = ''
  editingBalanceId.value = balance?.balanceId ?? null
  selectedStock.value = balance
    ? {
        stockId: balance.stockId,
        stockCode: balance.stockCode,
        stockName: balance.stockName,
        marketCode: null,
        currencyCode: balance.currencyCode,
      }
    : null
  form.value = balance
    ? {
        accountId: balance.accountId,
        stockId: balance.stockId,
        balanceQuantity: String(balance.balanceQuantity),
        currentPrice: balance.currentPrice === null ? '' : String(balance.currentPrice),
        purchaseAmount: balance.purchaseAmount === null ? '' : String(balance.purchaseAmount),
        purchasePrice: balance.purchasePrice === null ? '' : String(balance.purchasePrice),
        evaluationAmount: balance.evaluationAmount === null ? '' : String(balance.evaluationAmount),
        evaluationProfitLoss:
          balance.evaluationProfitLoss === null ? '' : String(balance.evaluationProfitLoss),
        returnRate: balance.returnRate === null ? '' : String(balance.returnRate),
      }
    : {
        accountId: accountId.value,
        stockId: '',
        balanceQuantity: '0',
        currentPrice: '',
        purchaseAmount: '',
        purchasePrice: '',
        evaluationAmount: '',
        evaluationProfitLoss: '',
        returnRate: '',
      }
  editing.value = true
}
async function deleteBalance(balance: Balance) {
  if (
    !window.confirm(
      `${balance.stockName} (${balance.stockCode}) 잔고를 삭제할까요? 반영된 거래는 미반영 상태로 돌아갑니다.`,
    )
  )
    return
  clearError()
  busy.value = true
  try {
    await stockBalanceApi.delete(balance.balanceId)
    await refresh()
    notice.value = '잔고가 삭제되었습니다.'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '잔고를 삭제하지 못했습니다.'
  } finally {
    busy.value = false
  }
}
function openApplyDialog() {
  error.value = ''
  pendingFilters.value = { dateFrom: '', dateTo: '', keyword: '' }
  pendingPage.value = 0
  applying.value = true
  void loadPending()
}
function closeApplyDialog() {
  applying.value = false
  selectedIds.value = []
  groups.value = []
  error.value = ''
}
async function loadStockPicker() {
  stockPickerLoading.value = true
  stockPickerError.value = ''
  try {
    stocks.value = await stockTradeApi.getStocks(
      stockPickerFilters.value.keyword,
      stockPickerFilters.value.marketCode,
    )
  } catch (e) {
    stockPickerError.value = e instanceof Error ? e.message : '종목을 검색하지 못했습니다.'
  } finally {
    stockPickerLoading.value = false
  }
}
async function openStockPicker() {
  showStockPicker.value = true
  await loadStockPicker()
}
function selectStock(stock: Stock) {
  form.value.stockId = stock.stockId
  selectedStock.value = stock
  showStockPicker.value = false
  error.value = ''
}
async function save() {
  clearError()
  if (!form.value.accountId || !form.value.stockId) {
    error.value = '계좌와 종목을 선택해 주세요.'
    return
  }
  const blank = (value: string | number) => String(value).trim() || null
  busy.value = true
  try {
    const payload: BalancePayload = {
      accountId: Number(form.value.accountId),
      stockId: Number(form.value.stockId),
      balanceQuantity: String(form.value.balanceQuantity).trim(),
      currentPrice: blank(form.value.currentPrice),
      purchaseAmount: blank(form.value.purchaseAmount),
      purchasePrice: blank(form.value.purchasePrice),
      evaluationAmount: blank(form.value.evaluationAmount),
      evaluationProfitLoss: blank(form.value.evaluationProfitLoss),
      returnRate: blank(form.value.returnRate),
    }
    await stockBalanceApi.save(payload)
    editing.value = false
    await refresh()
    notice.value = '잔고를 저장했습니다.'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '잔고를 저장하지 못했습니다.'
  } finally {
    busy.value = false
  }
}
async function preview() {
  clearError()
  if (!selectedIds.value.length) {
    error.value = '미반영 거래를 선택해 주세요.'
    return
  }
  try {
    groups.value = await stockBalanceApi.preview(selectedIds.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '반영 예상치를 조회하지 못했습니다.'
  }
}
async function apply() {
  clearError()
  busy.value = true
  try {
    await stockBalanceApi.apply(selectedIds.value)
    selectedIds.value = []
    groups.value = []
    await refresh()
    closeApplyDialog()
    notice.value = '선택 거래를 잔고에 반영했습니다.'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '거래를 반영하지 못했습니다.'
  } finally {
    busy.value = false
  }
}
async function showSnapshot(id: number) {
  try {
    selectedSnapshot.value = await stockBalanceApi.snapshot(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '스냅샷을 불러오지 못했습니다.'
  }
}
watch(selectedIds, () => {
  groups.value = []
})
onMounted(async () => {
  try {
    const [b, a] = await Promise.all([stockTradeApi.getBrokerages(), stockTradeApi.getAccounts()])
    brokerages.value = b
    accounts.value = a
    const tradeId = Number(route.query.tradeId)
    if (Number.isInteger(tradeId) && tradeId > 0) {
      const match = await stockTradeApi.getTrade(tradeId)
      if (!match.balanceApplied) {
        applying.value = true
        brokerageId.value = match.brokerageId
        accountId.value = match.accountId
        selectedIds.value = [tradeId]
      }
    } else {
      brokerageId.value = brokerages.value[0]?.brokerageId ?? ''
      accountId.value = filteredAccounts.value[0]?.accountId ?? ''
    }
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '기준정보를 불러오지 못했습니다.'
  }
})
</script>

<template>
  <div class="balance-shell">
    <main class="balance-page page-shell">
      <header class="balance-heading">
        <div>
          <p class="section-label">STOCK BALANCE</p>
          <h1>주식잔고</h1>
          <p>증권사와 계좌를 선택해 보유 현황을 확인하고 거래를 반영합니다.</p>
        </div>
      </header>
      <p v-if="error && !editing && !applying" class="alert">
        <i class="pi pi-exclamation-circle"></i>{{ error }}
      </p>
      <div v-if="notice" class="toast"><i class="pi pi-check-circle"></i>{{ notice }}</div>
      <div class="balance-grid">
        <section class="panel brokerage-panel">
          <header class="panel-heading">
            <div>
              <h2>증권사</h2>
              <span>{{ brokerages.length }}개</span>
            </div>
          </header>
          <div class="brokerage-list">
            <div v-if="!brokerages.length" class="empty">
              <i class="pi pi-building"></i><strong>등록된 증권사가 없습니다</strong>
              <span>증권사·계좌 관리에서 먼저 등록해 주세요.</span>
            </div>
            <button
              v-for="item in brokerages"
              :key="item.brokerageId"
              type="button"
              class="brokerage-item"
              :class="{ selected: brokerageId === item.brokerageId }"
              @click="selectBrokerage(item.brokerageId)"
            >
              <span class="company-icon"><i class="pi pi-building-columns"></i></span>
              <span class="company-copy">
                <strong>{{ item.brokerageName }}</strong>
                <small
                  >등록 계좌
                  {{ accounts.filter((a) => a.brokerageId === item.brokerageId).length }}개</small
                >
              </span>
            </button>
          </div>
        </section>

        <section class="panel account-panel">
          <header class="panel-heading">
            <div>
              <p>{{ selectedBrokerage?.brokerageName || '증권사를 선택해 주세요' }}</p>
              <h2>계좌</h2>
            </div>
            <span>{{ filteredAccounts.length }}개</span>
          </header>
          <div class="account-list">
            <div v-if="!brokerageId" class="empty">
              <i class="pi pi-arrow-left"></i><strong>증권사를 선택해 주세요</strong>
            </div>
            <div v-else-if="!filteredAccounts.length" class="empty">
              <i class="pi pi-wallet"></i><strong>등록된 계좌가 없습니다</strong>
            </div>
            <button
              v-for="item in filteredAccounts"
              :key="item.accountId"
              type="button"
              class="account-item"
              :class="{ selected: accountId === item.accountId }"
              @click="selectAccount(item.accountId)"
            >
              <span class="company-icon"><i class="pi pi-wallet"></i></span>
              <span class="company-copy">
                <strong>{{ item.accountName }}</strong>
                <small>{{ item.accountNumber || '계좌번호 미등록' }}</small>
              </span>
            </button>
          </div>
        </section>

        <section class="panel balance-panel">
          <header class="panel-heading balance-panel-heading">
            <div>
              <p>{{ selectedAccount?.accountName || '계좌 선택' }}</p>
              <h2>잔고현황</h2>
            </div>
            <div class="balance-actions">
              <button
                class="primary-button"
                type="button"
                :disabled="!accountId"
                @click="openForm()"
              >
                <i class="pi pi-plus"></i> 잔고 직접 입력
              </button>
              <button
                class="primary-button"
                type="button"
                :disabled="!accountId"
                @click="openApplyDialog"
              >
                <i class="pi pi-check-square"></i> 미반영 거래 반영 <b>{{ pendingTotal }}</b>
              </button>
            </div>
          </header>
          <div class="balance-content">
            <div class="balance-totals">
              <span v-for="[currency, amount] in totals" :key="currency"
                >평가금액 <strong>{{ fmt(amount) }} {{ currency }}</strong></span
              >
            </div>
            <DataTable
              :value="balances"
              dataKey="balanceId"
              class="account-style-table"
              scrollable
              :tableStyle="{ minWidth: '1050px' }"
              :emptyMessage="accountId ? '등록된 잔고가 없습니다.' : '계좌를 선택해 주세요.'"
            >
              <Column field="stockName" header="종목"
                ><template #body="{ data }"
                  >{{ data.stockName }} ({{ data.stockCode }})</template
                ></Column
              >
              <Column header="보유수량"
                ><template #body="{ data }">{{ fmt(data.balanceQuantity, 6) }}</template></Column
              >
              <Column header="매입금액"
                ><template #body="{ data }">{{ fmt(data.purchaseAmount) }}</template></Column
              >
              <Column header="평균매수가"
                ><template #body="{ data }">{{ fmt(data.purchasePrice, 6) }}</template></Column
              >
              <Column header="현재가"
                ><template #body="{ data }">{{ fmt(data.currentPrice, 6) }}</template></Column
              >
              <Column header="평가금액"
                ><template #body="{ data }">{{ fmt(data.evaluationAmount) }}</template></Column
              >
              <Column header="관리"
                ><template #body="{ data }"
                  ><div class="balance-row-actions">
                    <button
                      class="row-edit"
                      type="button"
                      title="수정"
                      aria-label="수정"
                      :disabled="busy"
                      @click="openForm(data)"
                    >
                      <i class="pi pi-pencil"></i>
                    </button>
                    <button
                      class="row-edit row-delete"
                      type="button"
                      title="삭제"
                      aria-label="삭제"
                      :disabled="busy"
                      @click="deleteBalance(data)"
                    >
                      <i class="pi pi-trash"></i>
                    </button></div></template
              ></Column>
            </DataTable>
            <section v-if="accountId" class="snapshot-section">
              <h3>잔고 스냅샷</h3>
              <div class="snapshot-list">
                <button
                  v-for="s in snapshots"
                  :key="s.snapshotId"
                  type="button"
                  :class="{ selected: selectedSnapshot?.header.snapshotId === s.snapshotId }"
                  @click="showSnapshot(s.snapshotId)"
                >
                  {{ s.snapshotAt }}
                </button>
                <span v-if="!snapshots.length" class="muted">저장된 스냅샷이 없습니다.</span>
              </div>
              <DataTable
                v-if="selectedSnapshot"
                :value="selectedSnapshot.details"
                class="account-style-table"
              >
                <Column field="stockName" header="종목" />
                <Column header="보유수량"
                  ><template #body="{ data }">{{ fmt(data.balanceQuantity, 6) }}</template></Column
                >
                <Column header="매입금액"
                  ><template #body="{ data }">{{ fmt(data.purchaseAmount) }}</template></Column
                >
                <Column header="평균매수가"
                  ><template #body="{ data }">{{ fmt(data.purchasePrice, 6) }}</template></Column
                >
              </DataTable>
            </section>
          </div>
        </section>
      </div>
      <div v-if="applying" class="modal-backdrop" @click.self="closeApplyDialog">
        <section
          class="modal balance-modal apply-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="balance-apply-title"
        >
          <header>
            <div>
              <p>STOCK BALANCE</p>
              <h2 id="balance-apply-title">미반영 거래 반영</h2>
            </div>
            <button type="button" aria-label="닫기" @click="closeApplyDialog">
              <i class="pi pi-times"></i>
            </button>
          </header>
          <div class="apply-modal-content">
            <p class="panel-description">
              {{ selectedBrokerage?.brokerageName }} · {{ selectedAccount?.accountName }}의 미반영
              매수·매도 거래를 조회하고 잔고에 반영합니다.
            </p>
            <form class="apply-search-form" @submit.prevent="searchPending">
              <label
                ><span>시작일</span><input v-model="pendingFilters.dateFrom" type="date"
              /></label>
              <label
                ><span>종료일</span><input v-model="pendingFilters.dateTo" type="date"
              /></label>
              <label
                ><span>종목명</span
                ><input v-model="pendingFilters.keyword" placeholder="종목명 또는 종목코드"
              /></label>
              <button class="primary-button" type="submit" :disabled="pendingLoading">
                <i class="pi pi-search"></i> 조회
              </button>
            </form>
            <p v-if="pendingError" class="alert error" role="alert">{{ pendingError }}</p>
            <DataTable
              :value="pending"
              dataKey="tradeId"
              class="account-style-table"
              :loading="pendingLoading"
              scrollable
              :tableStyle="{ minWidth: '920px' }"
              :emptyMessage="accountId ? '미반영 거래가 없습니다.' : '계좌를 선택해 주세요.'"
            >
              <Column header="선택"
                ><template #body="{ data }"
                  ><input
                    v-model="selectedIds"
                    type="checkbox"
                    :value="data.tradeId"
                    :aria-label="`${data.stockName} 거래 선택`" /></template
              ></Column>
              <Column field="tradeDate" header="거래일" />
              <Column field="stockName" header="종목" />
              <Column field="tradeType" header="구분"
                ><template #body="{ data }">{{
                  data.tradeType === 'BUY' ? '매수' : '매도'
                }}</template></Column
              >
              <Column header="수량"
                ><template #body="{ data }">{{ fmt(data.executionQuantity, 6) }}</template></Column
              >
              <Column header="체결단가"
                ><template #body="{ data }">{{ fmt(data.executionPrice, 6) }}</template></Column
              >
              <Column header="거래금액"
                ><template #body="{ data }">{{ fmt(data.executionAmount) }}</template></Column
              >
            </DataTable>
            <div class="panel-toolbar">
              <button
                class="secondary"
                type="button"
                :disabled="pendingLoading || pendingPage === 0"
                @click="changePendingPage(-1)"
              >
                이전
              </button>
              <span>{{ pendingPage + 1 }} / {{ Math.max(1, Math.ceil(pendingTotal / 100)) }}</span>
              <button
                class="secondary"
                type="button"
                :disabled="pendingLoading || (pendingPage + 1) * 100 >= pendingTotal"
                @click="changePendingPage(1)"
              >
                다음
              </button>
              <button
                class="primary"
                type="button"
                :disabled="pendingLoading || !selectedIds.length"
                @click="preview"
              >
                반영 예상
              </button>
              <button
                class="primary-button"
                type="button"
                :disabled="pendingLoading || busy || !selectedIds.length"
                @click="apply"
              >
                <i class="pi pi-check"></i> 선택 거래 반영 ({{ selectedIds.length }})
              </button>
            </div>
            <div v-if="groups.length" class="balance-preview">
              <h3>반영 예상</h3>
              <div v-for="g in groups" :key="key(g)" class="balance-preview-row">
                <strong>{{ g.stockName }}</strong>
                <span
                  >현재 {{ fmt(g.currentQuantity, 6) }} → 반영 후
                  {{ fmt(g.expectedQuantity, 6) }}</span
                >
              </div>
              <button class="primary" type="button" :disabled="busy" @click="apply">
                예상대로 반영
              </button>
            </div>
            <p v-if="error" class="alert"><i class="pi pi-exclamation-circle"></i>{{ error }}</p>
          </div>
        </section>
      </div>
      <div v-if="editing" class="modal-backdrop" @click.self="editing = false">
        <section
          class="modal balance-modal balance-entry-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="balance-form-title"
        >
          <header>
            <div>
              <p>STOCK BALANCE</p>
              <h2 id="balance-form-title">
                {{ editingBalanceId ? '잔고 수정' : '잔고 직접 입력' }}
              </h2>
            </div>
            <button class="close-button" type="button" aria-label="닫기" @click="editing = false">
              <i class="pi pi-times"></i>
            </button>
          </header>
          <form @submit.prevent="save">
            <div class="balance-form-grid">
              <label
                ><span class="required-label"><b>*</b> 계좌</span>
                <div class="balance-selected-account">
                  {{ selectedBrokerage?.brokerageName }} · {{ selectedAccount?.accountName }}
                </div></label
              ><label
                ><span class="required-label"><b>*</b> 종목</span>
                <div class="balance-stock-picker-field">
                  <div class="balance-selected-stock">
                    {{
                      selectedStock
                        ? `${selectedStock.stockName} (${selectedStock.stockCode})${selectedStock.marketCode ? ` · ${selectedStock.marketCode}` : ''}`
                        : '종목을 선택해 주세요.'
                    }}
                  </div>
                  <button
                    type="button"
                    title="종목 검색"
                    aria-label="종목 검색"
                    @click="openStockPicker"
                  >
                    <i class="pi pi-search"></i>
                  </button></div></label
              ><label
                v-for="field in [
                  ['balanceQuantity', '보유수량'],
                  ['purchaseAmount', '매입금액'],
                  ['purchasePrice', '평균매수가'],
                  ['currentPrice', '현재가'],
                  ['evaluationAmount', '평가금액'],
                  ['evaluationProfitLoss', '평가손익'],
                  ['returnRate', '수익률'],
                ] as const"
                :key="field[0]"
                ><span><b v-if="field[0] === 'balanceQuantity'">*</b> {{ field[1] }}</span>
                <input
                  v-model="form[field[0]]"
                  type="number"
                  :required="field[0] === 'balanceQuantity'"
                  :min="
                    field[0] === 'evaluationProfitLoss' || field[0] === 'returnRate' ? undefined : 0
                  "
                  :step="
                    field[0] === 'purchaseAmount' ||
                    field[0] === 'evaluationAmount' ||
                    field[0] === 'evaluationProfitLoss'
                      ? '0.01'
                      : '0.000001'
                  "
              /></label>
            </div>
            <div v-if="error" class="alert error" role="alert">
              <i class="pi pi-exclamation-circle"></i>{{ error }}
            </div>
            <footer>
              <button class="secondary-button" type="button" @click="editing = false">취소</button>
              <button class="primary-button" type="submit" :disabled="busy">
                <i :class="busy ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i>
                {{ busy ? '저장 중' : '잔고 저장' }}
              </button>
            </footer>
          </form>
        </section>
      </div>
      <div
        v-if="showStockPicker"
        class="modal-backdrop balance-stock-picker-backdrop"
        @click.self="showStockPicker = false"
      >
        <section
          class="modal balance-stock-picker-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="balance-stock-picker-title"
        >
          <header>
            <div>
              <p>STOCK SEARCH</p>
              <h2 id="balance-stock-picker-title">종목 선택</h2>
            </div>
            <button
              class="close-button"
              type="button"
              aria-label="종목 검색 닫기"
              @click="showStockPicker = false"
            >
              <i class="pi pi-times"></i>
            </button>
          </header>
          <form class="balance-stock-search" @submit.prevent="loadStockPicker">
            <input
              v-model="stockPickerFilters.keyword"
              placeholder="종목코드 또는 종목명"
              aria-label="종목코드 또는 종목명"
              autofocus
            />
            <input
              v-model="stockPickerFilters.marketCode"
              placeholder="시장코드"
              aria-label="시장코드"
            />
            <button class="primary-button" type="submit"><i class="pi pi-search"></i> 조회</button>
          </form>
          <p v-if="stockPickerError" class="alert error" role="alert">{{ stockPickerError }}</p>
          <div class="balance-stock-picker-table">
            <DataTable
              :value="stocks"
              dataKey="stockId"
              :loading="stockPickerLoading"
              @row-click="selectStock($event.data)"
            >
              <Column field="stockCode" header="종목코드" />
              <Column field="stockName" header="종목명" />
              <Column field="marketCode" header="시장코드" />
              <Column field="currencyCode" header="통화" />
              <Column header="선택"
                ><template #body="{ data }"
                  ><button class="secondary-button" type="button" @click.stop="selectStock(data)">
                    선택
                  </button></template
                ></Column
              >
              <template #empty>
                <div class="empty-state">
                  {{ stockPickerLoading ? '조회 중입니다.' : '검색 결과가 없습니다.' }}
                </div>
              </template>
            </DataTable>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.balance-page {
  display: grid;
  gap: 20px;
  margin-inline: auto;
}
.balance-page > .balance-heading {
  margin-bottom: 0 !important;
}
.balance-heading h1 {
  margin: 0;
}
.balance-grid {
  display: grid;
  grid-template-columns: minmax(168px, 0.574fr) minmax(182px, 0.63fr) minmax(520px, 2.316fr);
  align-items: stretch;
  gap: 16px;
}
.panel {
  display: flex;
  min-width: 0;
  min-height: 620px;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}
.panel-heading {
  display: flex;
  min-height: 82px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--app-border);
}
.panel-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}
.panel-heading p {
  margin: 0 0 5px;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}
.panel-heading > div > span,
.panel-heading > span {
  color: var(--app-text-muted);
  font-size: 12px;
}
.brokerage-list,
.account-list {
  flex: 1;
  overflow: auto;
  padding: 8px;
}
.brokerage-item,
.account-item {
  display: grid;
  width: 100%;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  padding: 12px;
  color: var(--app-text);
  background: transparent;
  border: 1px solid transparent;
  text-align: left;
  cursor: pointer;
}
.brokerage-item:hover,
.account-item:hover {
  background: var(--app-surface-muted);
}
.brokerage-item.selected,
.account-item.selected {
  background: var(--app-primary-50);
  border-color: var(--app-primary-200);
}
.company-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: var(--app-primary-700);
  background: var(--app-primary-100);
}
.company-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}
.company-copy strong,
.company-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.company-copy small,
.muted {
  color: var(--app-text-muted);
  font-size: 11px;
}
.empty {
  display: grid;
  min-height: 160px;
  align-content: center;
  justify-items: center;
  gap: 9px;
  padding: 20px;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}
.empty i {
  color: var(--app-primary-500);
  font-size: 22px;
}
.balance-panel {
  overflow: hidden;
}
.balance-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.balance-actions button {
  flex: 0 0 auto;
}
.balance-content {
  min-width: 0;
  flex: 1;
  overflow: auto;
}
.balance-totals {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 16px;
  padding: 8px 18px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.balance-totals strong {
  margin-left: 5px;
  color: var(--app-text);
}
.balance-panel .account-style-table {
  width: calc(100% - 16px);
  margin: 4px 8px;
}
.balance-panel .account-style-table .p-datatable-table {
  min-width: 1050px;
}
.balance-row-actions {
  display: flex;
  gap: 5px;
}
.row-edit {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  color: var(--app-text-muted);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}
.row-edit:hover {
  color: var(--app-primary-700);
  background: var(--app-primary-50);
}
.row-delete:hover {
  color: var(--app-danger);
  background: var(--app-danger-soft);
}
.snapshot-section {
  margin: 18px 14px;
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}
.snapshot-section h3,
.balance-preview h3 {
  margin: 0 0 10px;
  color: var(--app-text);
  font-size: 14px;
}
.snapshot-list,
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 14px;
}
.snapshot-list button {
  min-height: 32px;
  padding: 0 10px;
  color: var(--app-text-muted);
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border);
  font-size: 11px;
}
.snapshot-list button.selected {
  color: var(--app-primary-800);
  background: var(--app-primary-50);
  border-color: var(--app-primary-200);
}
.panel-description {
  margin: 14px 16px 2px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.panel-toolbar {
  justify-content: flex-end;
}
.panel-toolbar > span {
  color: var(--app-text-muted);
  font-size: 12px;
}
.panel-toolbar button {
  min-height: 38px;
}
.balance-preview {
  margin: 12px 14px 18px;
  padding: 14px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border);
}
.balance-preview-row {
  display: grid;
  grid-template-columns: minmax(90px, 0.7fr) minmax(130px, 1fr);
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border);
  font-size: 12px;
}
.balance-preview-row span {
  color: var(--app-text-muted);
}
.balance-preview-row label {
  display: grid;
  gap: 5px;
  color: var(--app-text-muted);
  font-size: 11px;
}
.apply-modal {
  width: min(1014px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
  background: var(--app-surface);
}
.apply-modal-content {
  padding: 16px;
}
.apply-modal-content .panel-description {
  margin: 0 0 12px;
}
.apply-search-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr)) minmax(150px, 1fr) auto;
  align-items: end;
  gap: 10px;
  margin-bottom: 14px;
}
.apply-modal-content .account-style-table {
  width: 100%;
  margin: 0;
}
.apply-modal-content .balance-preview {
  margin: 12px 0 0;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 20px;
  overflow: auto;
  backdrop-filter: blur(3px);
}
.apply-modal > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 23px 25px 17px;
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
}
.apply-modal > header p {
  margin: 0 0 8px;
  color: var(--app-primary-700);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}
.apply-modal > header h2 {
  margin: 0;
  font-size: 24px;
}
.apply-modal > header button {
  width: 34px;
  height: 34px;
  color: var(--app-text-muted);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}
.balance-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 17px;
}
.balance-stock-picker-backdrop {
  z-index: 110;
}
.balance-stock-search {
  display: grid;
  grid-template-columns: 1fr 180px auto;
  gap: 10px;
  padding: 20px 28px;
}
.balance-stock-picker-table {
  overflow-x: auto;
}
.balance-stock-picker-table :deep(.p-datatable-tbody > tr) {
  cursor: pointer;
}
@media (max-width: 1180px) {
  .balance-grid {
    grid-template-columns: minmax(220px, 0.8fr) minmax(240px, 1fr);
  }
  .balance-panel {
    grid-column: 1 / -1;
    min-height: 520px;
  }
}
@media (max-width: 700px) {
  .apply-search-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .balance-stock-search {
    grid-template-columns: 1fr;
    padding: 18px 20px;
  }
  .balance-panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .balance-grid {
    grid-template-columns: 1fr;
  }
  .panel {
    min-height: auto;
  }
  .brokerage-list,
  .account-list {
    max-height: 260px;
  }
  .balance-panel {
    grid-column: auto;
    min-height: 480px;
  }
  .balance-preview-row {
    grid-template-columns: 1fr;
  }
  .balance-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
