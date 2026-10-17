<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  investmentStrategyApi,
  investmentHorizons,
  strategyScopes,
  type RichContent,
  type StrategyPayload,
} from '../services/investmentStrategyApi'
import { stockTradeApi, type Stock } from '../services/stockTradeApi'
import RichTextEditor from '../components/RichTextEditor.vue'
import { useContentAttachments } from '../utils/useContentAttachments'
import { formatBytes } from '../utils/journalContent'
const route = useRoute(),
  router = useRouter(),
  id = computed(() => (route.params.strategyId ? Number(route.params.strategyId) : null)),
  title = ref(''),
  scope = ref<'PORTFOLIO' | 'THEME' | 'STOCK'>('PORTFOLIO'),
  strategyType = ref(''),
  investmentHorizon = ref(''),
  status = ref<StrategyPayload['status']>('DRAFT'),
  content = ref<RichContent>({ type: 'doc', content: [] }),
  stocks = ref<Stock[]>([]),
  stockKeyword = ref(''),
  selected = ref<number[]>([]),
  pendingSelected = ref<number[]>([]),
  showStockPicker = ref(false),
  loading = ref(!!id.value),
  saving = ref(false),
  error = ref('')
const attachments = useContentAttachments(content)
const selectedStocks = computed(() =>
  selected.value
    .map((stockId) => stocks.value.find((stock) => stock.stockId === stockId))
    .filter((stock): stock is Stock => Boolean(stock)),
)
const availableTypes = computed(
  () => strategyScopes.find((item) => item.code === scope.value)?.types ?? [],
)
function changeScope() {
  if (!availableTypes.value.some((type) => type[0] === strategyType.value)) {
    strategyType.value = ''
  }
}
const filtered = computed(() => {
  const q = stockKeyword.value.trim().toLowerCase()
  return stocks.value.filter(
    (s) => !q || s.stockName.toLowerCase().includes(q) || s.stockCode.toLowerCase().includes(q),
  )
})
function openStockPicker() {
  pendingSelected.value = [...selected.value]
  stockKeyword.value = ''
  showStockPicker.value = true
}
function closeStockPicker() {
  showStockPicker.value = false
}
function confirmStockSelection() {
  selected.value = [...pendingSelected.value]
  closeStockPicker()
}
function removeStock(stockId: number) {
  selected.value = selected.value.filter((id) => id !== stockId)
}
onMounted(async () => {
  try {
    stocks.value = await stockTradeApi.getStocks()
    if (id.value) {
      const s = await investmentStrategyApi.detail(id.value)
      title.value = s.title
      scope.value = s.strategyScope
      strategyType.value = s.strategyType || ''
      investmentHorizon.value = s.investmentHorizon || ''
      status.value = s.status
      content.value = s.content
      attachments.existing.value = s.files
      selected.value = s.stocks.map((x) => x.stockId)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '정보를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})
async function save() {
  if (!title.value.trim()) {
    error.value = '전략명을 입력해 주세요.'
    return
  }
  if (scope.value === 'STOCK' && !selected.value.length) {
    error.value = '종목 중심 전략은 관련 종목을 선택해 주세요.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const { files, ...filePayload } = attachments.submission()
    const payload: StrategyPayload = {
      strategyScope: scope.value,
      strategyType: strategyType.value,
      investmentHorizon: investmentHorizon.value,
      title: title.value.trim(),
      content: content.value,
      status: status.value,
      stockIds: selected.value,
      ...filePayload,
    }
    const result = id.value
      ? await investmentStrategyApi.update(id.value, payload, files)
      : await investmentStrategyApi.create(payload, files)
    router.push(`/strategies/${result.strategyId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <div class="strategy-page">
    <header class="strategy-head">
      <div>
        <p>INVESTMENT STRATEGY</p>

        <h1>{{ id ? '투자전략 수정' : '투자전략 등록' }}</h1>
        <span>현재 유효한 투자 원칙과 실행 계획을 작성합니다.</span>
      </div>
    </header>
    <p v-if="error" class="strategy-alert">{{ error }}</p>
    <p v-if="attachments.error.value" class="strategy-alert">{{ attachments.error.value }}</p>
    <p v-if="loading" class="empty">불러오는 중입니다.</p>
    <section v-else class="strategy-card strategy-form">
      <div class="strategy-main-fields">
        <label class="wide"
          ><span class="required-label"><b>*</b> 전략명</span
          ><input v-model="title" maxlength="200" placeholder="전략명을 입력하세요" /></label
        ><label
          >전략 적용 범위<select v-model="scope" @change="changeScope">
            <option v-for="item in strategyScopes" :key="item.code" :value="item.code">
              {{ item.name }}
            </option>
          </select></label
        ><label
          >전략 유형<select v-model="strategyType">
            <option value="">선택 안 함</option>
            <option v-for="type in availableTypes" :key="type[0]" :value="type[0]">
              {{ type[1] }}
            </option>
          </select></label
        ><label
          >투자 기간<select v-model="investmentHorizon">
            <option value="">선택 안 함</option>
            <option v-for="horizon in investmentHorizons" :key="horizon[0]" :value="horizon[0]">
              {{ horizon[1] }}
            </option>
          </select></label
        >
      </div>
      <div class="stock-picker">
        <div class="stock-picker-heading">
          <label>관련 종목</label>
          <button class="secondary" type="button" @click="openStockPicker">
            <i class="pi pi-search"></i> 종목 검색
          </button>
        </div>
        <div v-if="selectedStocks.length" class="selected-stock-list">
          <div v-for="stock in selectedStocks" :key="stock.stockId" class="selected-stock-item">
            <span
              ><strong>{{ stock.stockName }}</strong
              ><small>{{ stock.stockCode }} · {{ stock.marketCode || '시장 미지정' }}</small></span
            ><button
              type="button"
              :aria-label="`${stock.stockName} 선택 해제`"
              @click="removeStock(stock.stockId)"
            >
              <i class="pi pi-times"></i>
            </button>
          </div>
        </div>
        <p v-else class="selected-stock-empty">선택된 종목이 없습니다.</p>
      </div>
      <label class="body-label">투자전략 본문</label
      ><RichTextEditor
        v-model="content"
        :strategy-id="id || undefined"
        :images-enabled="true"
        @image-selected="attachments.addImage"
      />
      <div class="attachments">
        <div class="attachments-head">
          <div>
            <h2>첨부파일</h2>
            <p>파일당 25MB, 최대 30개</p>
          </div>
          <label class="file-picker"
            ><i class="pi pi-paperclip" /> 파일 첨부<input
              type="file"
              multiple
              @change="attachments.addFiles"
          /></label>
        </div>
        <ul class="file-list">
          <li v-for="file in attachments.existing.value" :key="`old-${file.fileId}`">
            <span
              >{{ file.originalFileName }}
              <small>{{ formatBytes(file.fileSize) }} · 기존</small></span
            >
            <button type="button" class="danger" @click="attachments.removeExisting(file)">
              제거
            </button>
          </li>
          <li v-for="item in attachments.pending.value" :key="item.key">
            <span
              >{{ item.file.name }}
              <small
                >{{ formatBytes(item.file.size) }} ·
                {{ item.type === 'IMAGE' ? '본문 이미지' : '신규 파일' }}</small
              ></span
            >
            <button type="button" class="danger" @click="attachments.removePending(item)">
              제거
            </button>
          </li>
          <li
            v-if="!attachments.existing.value.length && !attachments.pending.value.length"
            class="empty"
          >
            첨부파일이 없습니다.
          </li>
        </ul>
      </div>
      <footer>
        <button class="secondary" @click="router.push(id ? `/strategies/${id}` : '/strategies')">
          취소</button
        ><button class="primary" :disabled="saving" @click="save">
          {{ saving ? '저장 중...' : '저장' }}
        </button>
      </footer>
    </section>
    <div v-if="showStockPicker" class="strategy-backdrop" @click.self="closeStockPicker">
      <section class="strategy-modal wide stock-search-modal" role="dialog" aria-modal="true">
        <header>
          <h2>관련 종목 선택</h2>
          <button type="button" aria-label="종목 검색 닫기" @click="closeStockPicker">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="modal-body">
          <div class="stock-search-field">
            <i class="pi pi-search"></i>
            <input v-model="stockKeyword" placeholder="종목코드 또는 종목명 검색" autofocus />
          </div>
          <div v-if="filtered.length" class="stock-options">
            <label v-for="stock in filtered" :key="stock.stockId" class="check">
              <input v-model="pendingSelected" type="checkbox" :value="stock.stockId" /><span
                ><strong>{{ stock.stockName }}</strong
                ><small
                  >{{ stock.stockCode }} · {{ stock.marketCode || '시장 미지정' }}</small
                ></span
              >
            </label>
          </div>
          <p v-else class="selected-stock-empty">검색 결과가 없습니다.</p>
        </div>
        <footer>
          <span class="stock-selection-count">{{ pendingSelected.length }}개 선택</span>
          <button class="secondary" type="button" @click="closeStockPicker">취소</button>
          <button class="primary" type="button" @click="confirmStockSelection">선택 완료</button>
        </footer>
      </section>
    </div>
  </div>
</template>
<style scoped src="../assets/strategy.css"></style>
