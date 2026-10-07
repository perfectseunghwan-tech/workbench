<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  stockTradeApi,
  type Brokerage,
  type InvestmentAccount,
  type Stock,
  type StockTrade,
  type TradePayload,
} from '@/services/stockTradeApi'

interface TradeForm {
  brokerageId: number | ''
  accountId: number | ''
  stockId: number | ''
  tradeDate: string
  tradeType: 'BUY' | 'SELL'
  executionQuantity: string
  executionPrice: string
  purchasePrice: string
  realizedProfitLoss: string
  tradeMemo: string
}

const today = () => new Date().toISOString().slice(0, 10)
const emptyForm = (): TradeForm => ({
  brokerageId: '', accountId: '', stockId: '', tradeDate: today(), tradeType: 'BUY',
  executionQuantity: '', executionPrice: '', purchasePrice: '', realizedProfitLoss: '', tradeMemo: '',
})

const trades = ref<StockTrade[]>([])
const brokerages = ref<Brokerage[]>([])
const accounts = ref<InvestmentAccount[]>([])
const formAccounts = ref<InvestmentAccount[]>([])
const stocks = ref<Stock[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const showForm = ref(false)
const editingId = ref<number | null>(null)
const page = ref(0)
const pageSize = 20
const totalElements = ref(0)
const totalPages = ref(0)
const filters = ref({
  dateFrom: '', dateTo: '', brokerageId: '' as number | '', accountId: '' as number | '',
  marketCode: '', keyword: '', tradeType: '' as '' | 'BUY' | 'SELL',
})
const form = ref<TradeForm>(emptyForm())

const markets = computed(() =>
  [...new Set(stocks.value.map((item) => item.marketCode).filter(Boolean))].sort() as string[],
)
const executionAmount = computed(() => {
  const amount = Number(form.value.executionQuantity) * Number(form.value.executionPrice)
  return Number.isFinite(amount) ? amount : 0
})
const currentBuyAmount = computed(() => trades.value
  .filter((item) => item.tradeType === 'BUY')
  .reduce((sum, item) => sum + Number(item.executionAmount), 0))
const currentSellAmount = computed(() => trades.value
  .filter((item) => item.tradeType === 'SELL')
  .reduce((sum, item) => sum + Number(item.executionAmount), 0))

function formatNumber(value: number | string | null, digits = 2) {
  if (value === null || value === '') return '—'
  return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: digits }).format(Number(value))
}

function displayCurrency(value: number | string | null, currency = 'KRW') {
  return value === null ? '—' : `${formatNumber(value)} ${currency}`
}

function setNotice(message: string) {
  noticeMessage.value = message
  window.setTimeout(() => { if (noticeMessage.value === message) noticeMessage.value = '' }, 3000)
}

async function loadReferenceData() {
  const [brokerageData, accountData, stockData] = await Promise.all([
    stockTradeApi.getBrokerages(), stockTradeApi.getAccounts(), stockTradeApi.getStocks(),
  ])
  brokerages.value = brokerageData
  accounts.value = accountData
  formAccounts.value = accountData
  stocks.value = stockData
}

async function loadTrades() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = new URLSearchParams({ page: String(page.value), size: String(pageSize) })
    Object.entries(filters.value).forEach(([key, value]) => {
      if (value !== '') params.set(key, String(value))
    })
    const result = await stockTradeApi.getTrades(params)
    trades.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '거래 내역을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}

async function search() { page.value = 0; await loadTrades() }

async function resetFilters() {
  filters.value = { dateFrom: '', dateTo: '', brokerageId: '', accountId: '', marketCode: '', keyword: '', tradeType: '' }
  accounts.value = await stockTradeApi.getAccounts()
  await search()
}

async function onFilterBrokerageChange() {
  filters.value.accountId = ''
  accounts.value = await stockTradeApi.getAccounts(filters.value.brokerageId || undefined)
}

async function onFormBrokerageChange() {
  form.value.accountId = ''
  formAccounts.value = await stockTradeApi.getAccounts(form.value.brokerageId || undefined)
}

function openCreateForm(type: 'BUY' | 'SELL' = 'BUY') {
  editingId.value = null
  form.value = { ...emptyForm(), tradeType: type }
  formAccounts.value = [...accounts.value]
  errorMessage.value = ''
  showForm.value = true
}

async function openEditForm(trade: StockTrade) {
  editingId.value = trade.tradeId
  formAccounts.value = await stockTradeApi.getAccounts(trade.brokerageId)
  form.value = {
    brokerageId: trade.brokerageId, accountId: trade.accountId, stockId: trade.stockId,
    tradeDate: trade.tradeDate, tradeType: trade.tradeType,
    executionQuantity: String(trade.executionQuantity), executionPrice: String(trade.executionPrice),
    purchasePrice: trade.purchasePrice === null ? '' : String(trade.purchasePrice),
    realizedProfitLoss: trade.realizedProfitLoss === null ? '' : String(trade.realizedProfitLoss),
    tradeMemo: trade.tradeMemo ?? '',
  }
  errorMessage.value = ''
  showForm.value = true
}

function closeForm() { if (!saving.value) showForm.value = false }

function buildPayload(): TradePayload {
  if (!form.value.accountId || !form.value.stockId || !form.value.tradeDate) {
    throw new Error('계좌, 종목, 거래일을 모두 입력해 주세요.')
  }
  if (!form.value.executionQuantity || Number(form.value.executionQuantity) <= 0) {
    throw new Error('체결수량은 0보다 커야 합니다.')
  }
  if (form.value.executionPrice === '' || Number(form.value.executionPrice) < 0) {
    throw new Error('체결단가는 0 이상이어야 합니다.')
  }
  const sell = form.value.tradeType === 'SELL'
  return {
    accountId: Number(form.value.accountId), stockId: Number(form.value.stockId),
    tradeDate: form.value.tradeDate, tradeType: form.value.tradeType,
    executionQuantity: form.value.executionQuantity, executionPrice: form.value.executionPrice,
    purchasePrice: sell && form.value.purchasePrice !== '' ? form.value.purchasePrice : null,
    realizedProfitLoss: sell && form.value.realizedProfitLoss !== '' ? form.value.realizedProfitLoss : null,
    tradeMemo: form.value.tradeMemo.trim() || null,
  }
}

async function saveTrade() {
  saving.value = true
  errorMessage.value = ''
  try {
    const payload = buildPayload()
    if (editingId.value === null) await stockTradeApi.createTrade(payload)
    else await stockTradeApi.updateTrade(editingId.value, payload)
    setNotice(editingId.value === null ? '거래가 등록되었습니다.' : '거래가 수정되었습니다.')
    showForm.value = false
    await loadTrades()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '거래를 저장하지 못했습니다.'
  } finally { saving.value = false }
}

async function deleteTrade(trade: StockTrade) {
  const label = trade.tradeType === 'BUY' ? '매수' : '매도'
  if (!window.confirm(`${trade.stockName} ${label} 거래를 삭제할까요?`)) return
  try {
    await stockTradeApi.deleteTrade(trade.tradeId)
    setNotice('거래가 삭제되었습니다.')
    if (trades.value.length === 1 && page.value > 0) page.value -= 1
    await loadTrades()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '거래를 삭제하지 못했습니다.'
  }
}

async function changePage(nextPage: number) {
  if (nextPage < 0 || nextPage >= totalPages.value || nextPage === page.value) return
  page.value = nextPage
  await loadTrades()
}

onMounted(async () => {
  loading.value = true
  try { await loadReferenceData(); await loadTrades() }
  catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '기준정보를 불러오지 못했습니다.'
    loading.value = false
  }
})
</script>

<template>
  <div class="trade-page">
    <main class="page-shell">
      <section class="hero">
        <div class="hero-copy"><p class="section-label">STOCK JOURNAL</p><h1>매수·매도 기록</h1><p>투자 판단과 거래 결과를 한곳에서 차분하게 기록하세요.</p><button class="primary-button hero-action" type="button" @click="openCreateForm()"><i class="pi pi-plus"></i> 새 거래 기록</button></div>
        <div class="summary-grid" aria-label="거래 요약">
          <article><span>전체 거래</span><strong>{{ formatNumber(totalElements, 0) }}</strong><small>건</small></article>
          <article class="buy"><span>현재 페이지 매수</span><strong>{{ formatNumber(currentBuyAmount) }}</strong><small>원</small></article>
          <article class="sell"><span>현재 페이지 매도</span><strong>{{ formatNumber(currentSellAmount) }}</strong><small>원</small></article>
        </div>
      </section>

      <section class="filter-card">
        <div class="filter-heading"><h2><i class="pi pi-sliders-h"></i> 거래 찾기</h2><button class="text-button" type="button" @click="resetFilters"><i class="pi pi-refresh"></i> 초기화</button></div>
        <form class="filter-grid" @submit.prevent="search">
          <label><span>시작일</span><input v-model="filters.dateFrom" type="date" /></label>
          <label><span>종료일</span><input v-model="filters.dateTo" type="date" /></label>
          <label><span>증권사</span><select v-model="filters.brokerageId" @change="onFilterBrokerageChange"><option value="">전체 증권사</option><option v-for="item in brokerages" :key="item.brokerageId" :value="item.brokerageId">{{ item.brokerageName }}</option></select></label>
          <label><span>계좌</span><select v-model="filters.accountId"><option value="">전체 계좌</option><option v-for="item in accounts" :key="item.accountId" :value="item.accountId">{{ item.accountName }}</option></select></label>
          <label><span>거래 구분</span><select v-model="filters.tradeType"><option value="">전체</option><option value="BUY">매수</option><option value="SELL">매도</option></select></label>
          <label><span>시장</span><select v-model="filters.marketCode"><option value="">전체 시장</option><option v-for="market in markets" :key="market" :value="market">{{ market }}</option></select></label>
          <label class="keyword-field"><span>종목 검색</span><input v-model.trim="filters.keyword" placeholder="종목명 또는 종목코드" /></label>
          <button class="search-button" type="submit"><i class="pi pi-search"></i> 조회</button>
        </form>
      </section>

      <div v-if="errorMessage && !showForm" class="alert error" role="alert"><i class="pi pi-exclamation-circle"></i>{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="toast" role="status"><i class="pi pi-check-circle"></i>{{ noticeMessage }}</div>

      <section class="table-card">
        <div class="table-heading"><div><h2>거래 내역</h2><p>총 {{ formatNumber(totalElements, 0) }}건의 기록</p></div><div class="legend"><span><i class="dot buy-dot"></i>매수</span><span><i class="dot sell-dot"></i>매도</span></div></div>
        <div class="table-wrap">
          <table>
            <thead><tr><th>거래일</th><th>구분</th><th>종목</th><th>계좌</th><th class="number">수량</th><th class="number">체결단가</th><th class="number">체결금액</th><th class="number">실현손익</th><th aria-label="관리"></th></tr></thead>
            <tbody>
              <tr v-if="loading"><td colspan="9" class="empty-state"><i class="pi pi-spin pi-spinner"></i><strong>거래 내역을 불러오는 중입니다</strong></td></tr>
              <tr v-else-if="trades.length === 0"><td colspan="9" class="empty-state"><i class="pi pi-inbox"></i><strong>등록된 거래가 없습니다</strong><span>첫 매수 또는 매도 기록을 남겨보세요.</span><button type="button" @click="openCreateForm()">거래 기록하기</button></td></tr>
              <template v-else>
                <tr v-for="trade in trades" :key="trade.tradeId">
                  <td class="date-cell">{{ trade.tradeDate }}</td>
                  <td><span class="trade-badge" :class="trade.tradeType.toLowerCase()">{{ trade.tradeType === 'BUY' ? '매수' : '매도' }}</span></td>
                  <td><div class="stock-cell"><strong>{{ trade.stockName }}</strong><span>{{ trade.stockCode }} · {{ trade.marketCode || '시장 미지정' }}</span></div></td>
                  <td><div class="account-cell"><strong>{{ trade.accountName }}</strong><span>{{ trade.brokerageName }}</span></div></td>
                  <td class="number mono">{{ formatNumber(trade.executionQuantity, 6) }}</td>
                  <td class="number mono">{{ displayCurrency(trade.executionPrice, trade.currencyCode) }}</td>
                  <td class="number mono amount">{{ displayCurrency(trade.executionAmount, trade.currencyCode) }}</td>
                  <td class="number mono" :class="{ profit: Number(trade.realizedProfitLoss) > 0, loss: Number(trade.realizedProfitLoss) < 0 }">{{ displayCurrency(trade.realizedProfitLoss, trade.currencyCode) }}</td>
                  <td><div class="row-actions"><button title="수정" type="button" @click="openEditForm(trade)"><i class="pi pi-pencil"></i></button><button class="danger" title="삭제" type="button" @click="deleteTrade(trade)"><i class="pi pi-trash"></i></button></div></td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <nav v-if="totalPages > 1" class="pagination" aria-label="페이지 이동"><button type="button" :disabled="page === 0" @click="changePage(page - 1)"><i class="pi pi-chevron-left"></i></button><span><strong>{{ page + 1 }}</strong> / {{ totalPages }}</span><button type="button" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)"><i class="pi pi-chevron-right"></i></button></nav>
      </section>
    </main>

    <div v-if="showForm" class="modal-backdrop" @click.self="closeForm">
      <section class="trade-modal" role="dialog" aria-modal="true" aria-labelledby="trade-form-title">
        <header><div><p class="section-label">TRADE ENTRY</p><h2 id="trade-form-title">{{ editingId === null ? '새 거래 기록' : '거래 기록 수정' }}</h2></div><button class="close-button" type="button" aria-label="닫기" @click="closeForm"><i class="pi pi-times"></i></button></header>
        <form @submit.prevent="saveTrade">
          <div class="type-switch"><button type="button" :class="{ active: form.tradeType === 'BUY', buy: form.tradeType === 'BUY' }" @click="form.tradeType = 'BUY'"><i class="pi pi-arrow-down-left"></i> 매수</button><button type="button" :class="{ active: form.tradeType === 'SELL', sell: form.tradeType === 'SELL' }" @click="form.tradeType = 'SELL'"><i class="pi pi-arrow-up-right"></i> 매도</button></div>
          <div class="form-grid">
            <label><span>거래일 <b>*</b></span><input v-model="form.tradeDate" type="date" required /></label>
            <label><span>증권사 <b>*</b></span><select v-model="form.brokerageId" required @change="onFormBrokerageChange"><option value="" disabled>증권사 선택</option><option v-for="item in brokerages" :key="item.brokerageId" :value="item.brokerageId">{{ item.brokerageName }}</option></select></label>
            <label><span>계좌 <b>*</b></span><select v-model="form.accountId" required><option value="" disabled>계좌 선택</option><option v-for="item in formAccounts" :key="item.accountId" :value="item.accountId">{{ item.accountName }}{{ item.accountNumber ? ` · ${item.accountNumber}` : '' }}</option></select></label>
            <label><span>종목 <b>*</b></span><select v-model="form.stockId" required><option value="" disabled>종목 선택</option><option v-for="item in stocks" :key="item.stockId" :value="item.stockId">{{ item.stockName }} · {{ item.stockCode }}</option></select></label>
            <label><span>체결수량 <b>*</b></span><input v-model="form.executionQuantity" type="number" min="0.000001" step="0.000001" placeholder="0" required /></label>
            <label><span>체결단가 <b>*</b></span><input v-model="form.executionPrice" type="number" min="0" step="0.000001" placeholder="0" required /></label>
          </div>
          <div class="amount-preview"><span>예상 체결금액</span><strong>{{ formatNumber(executionAmount) }} <small>원</small></strong><p>최종 금액은 서버에서 다시 계산됩니다.</p></div>
          <div v-if="form.tradeType === 'SELL'" class="sell-fields"><p><i class="pi pi-info-circle"></i> MTS에서 확인한 매도 결과를 기록하세요.</p><div class="form-grid"><label><span>매입단가</span><input v-model="form.purchasePrice" type="number" min="0" step="0.000001" placeholder="선택 입력" /></label><label><span>실현손익</span><input v-model="form.realizedProfitLoss" type="number" step="0.01" placeholder="손실은 음수로 입력" /></label></div></div>
          <label class="memo-field"><span>거래 메모</span><textarea v-model="form.tradeMemo" rows="4" placeholder="매수·매도 이유와 당시의 판단을 기록해 보세요."></textarea></label>
          <div v-if="errorMessage" class="alert error" role="alert"><i class="pi pi-exclamation-circle"></i>{{ errorMessage }}</div>
          <footer><button class="secondary-button" type="button" @click="closeForm">취소</button><button class="primary-button" type="submit" :disabled="saving"><i :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i>{{ saving ? '저장 중' : editingId === null ? '거래 저장' : '수정 저장' }}</button></footer>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.trade-page{min-height:100vh;color:#18211d;background:#f3f5f1}button{width:auto}.topbar{height:74px;padding:0 max(24px,calc((100vw - 1320px)/2));display:flex;align-items:center;justify-content:space-between;background:rgba(250,251,248,.94);border-bottom:1px solid #dfe4dc;backdrop-filter:blur(12px);position:sticky;top:0;z-index:20}.brand{display:flex;align-items:center;gap:12px}.brand-mark{width:38px;height:38px;display:grid;place-items:center;color:#fff;background:#173f35;border-radius:11px}.brand div{display:grid;line-height:1.1}.brand small{margin-top:5px;color:#7b8580;font-size:11px}.page-shell{width:min(1320px,calc(100% - 48px));margin:auto;padding:54px 0 80px}.hero{display:flex;align-items:end;justify-content:space-between;gap:40px;margin-bottom:36px}.section-label{margin:0 0 10px;color:#598077;font-size:11px;font-weight:800;letter-spacing:.16em}.hero h1{margin:0;font:500 clamp(36px,5vw,58px) Georgia,serif;letter-spacing:-.045em}.hero>div>p:last-child{margin:13px 0 0;color:#727b76}.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(132px,1fr));gap:1px;overflow:hidden;min-width:500px;background:#dfe4dc;border:1px solid #dfe4dc;border-radius:16px}.summary-grid article{padding:18px 20px;background:#fafbf8}.summary-grid span{display:block;margin-bottom:9px;color:#7b8580;font-size:11px}.summary-grid strong{font:500 22px Georgia,serif}.summary-grid small{margin-left:4px;color:#8b948f}.summary-grid .buy strong{color:#245faa}.summary-grid .sell strong{color:#b45545}.filter-card,.table-card{background:#fff;border:1px solid #dfe4dc;border-radius:18px;box-shadow:0 8px 30px rgba(42,58,49,.04)}.filter-card{margin-bottom:22px;padding:22px 24px 24px}.filter-heading,.table-heading{display:flex;align-items:center;justify-content:space-between}.filter-heading{margin-bottom:18px}.filter-heading h2,.table-heading h2{margin:0;font-size:15px}.text-button{padding:5px;color:#727b76;background:transparent;font-size:12px}.filter-grid{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:14px;align-items:end}label{display:grid;gap:7px;color:#606b65;font-size:12px;font-weight:650}label span b{color:#b45545}input,select,textarea{width:100%;border:1px solid #d8ded9;border-radius:9px;background:#fcfdfb;color:#202a25;font:inherit;font-weight:500;outline:none}input,select{height:42px;padding:0 11px}textarea{padding:12px;resize:vertical;line-height:1.55}input:focus,select:focus,textarea:focus{border-color:#558176;box-shadow:0 0 0 3px rgba(85,129,118,.12)}.keyword-field{grid-column:span 2}.primary-button,.search-button{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:42px;padding:0 17px;background:#173f35;border-radius:9px;color:#fff;font-weight:700}.primary-button:hover,.search-button:hover{background:#0d3028}.table-heading{padding:22px 24px 17px}.table-heading p{margin:5px 0 0;color:#87908b;font-size:12px}.legend{display:flex;gap:16px;color:#69736e;font-size:12px}.legend span{display:flex;align-items:center;gap:6px}.dot{width:7px;height:7px;border-radius:50%}.buy-dot{background:#3978c7}.sell-dot{background:#c26050}.table-wrap{overflow-x:auto;border-top:1px solid #e5e9e4}table{width:100%;min-width:1120px;border-collapse:collapse}th{padding:12px 15px;color:#7c8681;background:#f8f9f7;font-size:11px;text-align:left;white-space:nowrap}td{padding:15px;border-top:1px solid #edf0ec;font-size:13px}tbody tr:hover{background:#fbfcfa}.number{text-align:right;white-space:nowrap}.mono{font-variant-numeric:tabular-nums}.amount{font-weight:750}.date-cell{color:#5f6964;white-space:nowrap}.stock-cell,.account-cell{display:grid;gap:4px;min-width:125px}.stock-cell span,.account-cell span{color:#89918d;font-size:11px}.trade-badge{display:inline-flex;padding:5px 9px;border-radius:6px;font-size:11px;font-weight:800}.trade-badge.buy{color:#245fa9;background:#eaf2fc}.trade-badge.sell{color:#a74739;background:#fceeea}.profit{color:#b45545;font-weight:750}.loss{color:#2769b8;font-weight:750}.row-actions{display:flex;justify-content:end;gap:5px}.row-actions button{width:31px;height:31px;padding:0;background:transparent;color:#6d7772;border:1px solid #e0e5e1}.row-actions .danger:hover{color:#ad4436;background:#fff3f0}.empty-state{height:260px;text-align:center;color:#87908b}.empty-state i,.empty-state strong,.empty-state span{display:block;margin:0 auto 10px}.empty-state i{font-size:30px}.empty-state button{margin-top:8px;padding:9px 13px;color:#173f35;background:#ecf2ef}.pagination{display:flex;justify-content:center;align-items:center;gap:16px;padding:16px;border-top:1px solid #e5e9e4;color:#6f7974;font-size:12px}.pagination button{width:34px;height:34px;padding:0;color:#344039;background:#f5f7f4}.alert{display:flex;align-items:center;gap:9px;margin:0 0 16px;padding:12px 15px;border-radius:9px;font-size:13px}.alert.error{color:#963f34;background:#fff0ed;border:1px solid #f5d8d2}.toast{position:fixed;right:26px;top:92px;z-index:50;display:flex;gap:9px;padding:13px 18px;color:#fff;background:#173f35;border-radius:10px}.modal-backdrop{position:fixed;inset:0;z-index:100;display:grid;place-items:center;padding:24px;background:rgba(17,29,23,.58);backdrop-filter:blur(3px);overflow:auto}.trade-modal{width:min(680px,100%);max-height:calc(100vh - 48px);overflow:auto;background:#fff;border-radius:20px}.trade-modal>header{display:flex;justify-content:space-between;padding:25px 28px 18px;border-bottom:1px solid #e4e8e4}.trade-modal h2{margin:0;font:500 28px Georgia,serif}.close-button{width:36px;height:36px;padding:0;color:#66716b;background:#f3f5f2}.trade-modal form{padding:22px 28px 28px}.type-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:22px;padding:5px;background:#f1f3f0;border-radius:11px}.type-switch button{padding:11px;color:#747e79;background:transparent;font-weight:750}.type-switch button.active{background:#fff;box-shadow:0 2px 8px rgba(30,48,38,.08)}.type-switch button.buy{color:#2769b8}.type-switch button.sell{color:#ae4b3d}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:17px}.amount-preview{margin:19px 0;padding:17px 19px;background:#eef3ef;border-radius:11px}.amount-preview span{display:block;color:#728078;font-size:11px}.amount-preview strong{display:block;margin-top:5px;color:#173f35;font:500 25px Georgia,serif}.amount-preview p{margin:5px 0 0;color:#8b958f;font-size:10px}.sell-fields{margin-bottom:18px;padding:16px;border:1px solid #eedfd9;background:#fffaf8;border-radius:11px}.sell-fields>p{margin:0 0 14px;color:#956b60;font-size:11px}.memo-field{margin-top:17px}.trade-modal footer{display:flex;justify-content:end;gap:9px;margin-top:23px;padding-top:20px;border-top:1px solid #e4e8e4}.secondary-button{padding:0 18px;color:#57625c;background:#eef1ee}
.trade-page{min-height:calc(100vh - 64px)}
.hero-copy>p:not(.section-label){margin:13px 0 0;color:#727b76}
.hero-action{margin-top:20px}
@media(max-width:980px){.hero{align-items:stretch;flex-direction:column}.summary-grid{min-width:0}.filter-grid{grid-template-columns:repeat(3,1fr)}}
@media(max-width:640px){.page-shell{width:calc(100% - 28px);padding-top:34px}.summary-grid{grid-template-columns:1fr}.filter-grid,.form-grid{grid-template-columns:1fr}.keyword-field{grid-column:auto}.modal-backdrop{padding:0;align-items:end}.trade-modal{max-height:94vh;border-radius:20px 20px 0 0}.trade-modal>header,.trade-modal form{padding-left:20px;padding-right:20px}.toast{left:14px;right:14px;top:78px}}
</style>
