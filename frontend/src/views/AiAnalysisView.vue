<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import RichTextEditor from '../components/RichTextEditor.vue'
import { collectImageReferences, emptyJournalContent, formatBytes } from '../utils/journalContent'
import type { JournalContent } from '../services/investmentJournalApi'
import {
  aiAnalysisApi,
  type AnalysisDetail,
  type AnalysisPayload,
  type AnalysisSummary,
  type AnalysisFile,
  type Relation,
  type Target,
} from '../services/aiAnalysisApi'

const types = {
  STOCK: '종목 분석',
  MARKET: '시장 분석',
  INDUSTRY: '산업 분석',
  PORTFOLIO: '포트폴리오 분석',
  STRATEGY: '투자전략 분석',
  MACRO: '거시경제 분석',
  ISSUE: '이슈 분석',
  COMPARISON: '비교 분석',
}
const purposes = {
  GENERAL: '일반 조사',
  NEW_BUY: '신규 매수 검토',
  ADD_BUY: '추가 매수 검토',
  HOLD: '보유 판단',
  SELL: '매도 검토',
  MID_LONG_TERM: '중장기 전망',
  RISK_REVIEW: '위험 검토',
  REGULAR_REVIEW: '정기 평가',
}
const sources = {
  CHATGPT_APP: 'ChatGPT 앱',
  CLAUDE_APP: 'Claude 앱',
  GPT_API: 'OpenAI API',
  CLAUDE_API: 'Claude API',
  OTHER_AI: '기타 AI',
}
const opinions = {
  BUY: '매수 검토',
  ADD: '추가 매수 검토',
  HOLD: '보유',
  WAIT: '관망',
  REDUCE: '비중 축소',
  SELL: '매도 검토',
  NEUTRAL: '중립',
  UNDETERMINED: '판단 유보',
}
const reviews = {
  UNREVIEWED: '미검토',
  REVIEWED: '검토 완료',
  ADOPTED: '전략 반영',
  PARTIAL: '일부 반영',
  REJECTED: '미채택',
}
const targetTypes = {
  STOCK: '종목',
  ETF: 'ETF',
  MARKET: '시장',
  INDUSTRY: '산업',
  PORTFOLIO: '포트폴리오',
  MACRO: '거시경제',
  OTHER: '기타',
}
const relationTypes = {
  STRATEGY: '투자전략',
  JOURNAL: '투자일지',
  TRADE: '거래',
  ACCOUNT: '계좌',
  ANALYSIS: 'AI 분석',
}
type Codes = Record<string, string>
const options = (codes: Codes) => Object.entries(codes).map(([value, label]) => ({ value, label }))
const label = (codes: Codes, code: string | null | undefined) => (code ? codes[code] || code : '—')
const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.analysisId) || 0)
const editing = computed(() => route.path.endsWith('/edit') || route.path.endsWith('/new'))
const listing = computed(() => !id.value && !editing.value)
const rows = ref<AnalysisSummary[]>([])
const total = ref(0)
const page = ref(0)
const detail = ref<AnalysisDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const filters = ref({
  dateFrom: '',
  dateTo: '',
  keyword: '',
  type: '',
  purpose: '',
  source: '',
  target: '',
  tag: '',
  review: '',
})
const form = ref(emptyForm())
const existing = ref<AnalysisFile[]>([])
const pending = ref<File[]>([])
const imageKeys = new Map<File, string>()
const pendingFileKeys = new WeakMap<File, string>()
function pendingFileKey(file: File) {
  let key = pendingFileKeys.get(file)
  if (!key) {
    key = crypto.randomUUID()
    pendingFileKeys.set(file, key)
  }
  return key
}
const richContent = ref<JournalContent>(emptyJournalContent())
const tagInput = ref('')
const tagLimit = 10
const tagCandidate = computed(() => tagInput.value.replace(/^#/, '').trim().replace(/\s+/g, ' '))
const canAddTag = computed(
  () =>
    !!tagCandidate.value &&
    form.value.tags.length < tagLimit &&
    !form.value.tags.includes(tagCandidate.value),
)
const editor = ref<{
  getHtml: () => string
  getJson: () => JournalContent
  setHtml: (html: string) => void
} | null>(null)
const relationType = ref('STRATEGY')
const relationKeyword = ref('')
const relationOptions = ref<{ id: number; title: string }[]>([])
const pendingRelations = ref<Relation[]>([])
const showRelationPicker = ref(false)
const relationLoading = ref(false)
let relationRequest = 0
const stockKeyword = ref('')
const stockOptions = ref<{ id: number; title: string }[]>([])
const stockSelection = ref<Target[]>([])
const showStockPicker = ref(false)
const stockLoading = ref(false)
const selectedStocks = computed(() =>
  form.value.targets.filter((target) => target.targetTypeCode === 'STOCK'),
)
function nowLocal() {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}
function emptyForm(): AnalysisPayload {
  return {
    title: '',
    analysisTypeCode: 'STOCK',
    analysisPurposeCode: '',
    summary: '',
    content: '',
    contentFormat: 'HTML',
    conclusion: '',
    keyRisks: '',
    sourceCode: 'CHATGPT_APP',
    sourceUrl: '',
    analyzedAt: nowLocal(),
    dataAsOf: '',
    opinionCode: '',
    reviewStatusCode: 'UNREVIEWED',
    userReview: '',
    targets: [],
    relations: [],
    tags: [],
    retainedFileIds: [],
    newFiles: [],
  }
}
async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const p = new URLSearchParams({ page: String(page.value), size: '20' })
    for (const [key, value] of Object.entries(filters.value))
      if (value.trim()) p.set(key, value.trim())
    const result = await aiAnalysisApi.list(p)
    rows.value = result.content
    total.value = result.totalElements
  } catch (e) {
    error.value = e instanceof Error ? e.message : '목록을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
async function loadDetail() {
  if (!id.value) {
    form.value = emptyForm()
    detail.value = null
    richContent.value = emptyJournalContent()
    return
  }
  loading.value = true
  error.value = ''
  try {
    const value = await aiAnalysisApi.detail(id.value)
    detail.value = value
    form.value = {
      ...value,
      analyzedAt: value.analyzedAt?.slice(0, 16),
      dataAsOf: value.dataAsOf?.slice(0, 16) || '',
      targets: value.targets.map((x) => ({ ...x })),
      relations: value.relations.map((x) => ({ ...x })),
      tags: [...value.tags],
      retainedFileIds: value.files.map((x) => x.fileId),
      newFiles: [],
    }
    existing.value = [...value.files]
    pending.value = []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '분석을 불러오지 못했습니다.'
  } finally {
    loading.value = false
    await nextTick()
    if (editing.value && detail.value?.contentFormat === 'HTML') {
      editor.value?.setHtml(detail.value.content)
      if (editor.value) richContent.value = editor.value.getJson()
    }
  }
}
watch(
  () => route.fullPath,
  () => {
    if (listing.value) loadList()
    else loadDetail()
  },
)
onMounted(() => {
  if (listing.value) loadList()
  else loadDetail()
})
watch(relationType, () => {
  relationOptions.value = []
})
function search() {
  page.value = 0
  loadList()
}
function onPage(event: { page: number }) {
  page.value = event.page
  loadList()
}
function reset() {
  filters.value = {
    dateFrom: '',
    dateTo: '',
    keyword: '',
    type: '',
    purpose: '',
    source: '',
    target: '',
    tag: '',
    review: '',
  }
  search()
}
function addTarget() {
  form.value.targets.push({
    targetTypeCode: 'OTHER',
    targetCode: '',
    targetName: '',
    isPrimary: form.value.targets.length === 0,
  })
}
async function findStocks() {
  stockLoading.value = true
  try {
    stockOptions.value = await aiAnalysisApi.stockOptions(stockKeyword.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '종목을 검색하지 못했습니다.'
  } finally {
    stockLoading.value = false
  }
}
function stockTarget(value: { id: number; title: string }): Target {
  const match = value.title.match(/^(.*) \(([^()]*)\)$/)
  return {
    targetTypeCode: 'STOCK',
    targetCode: match?.[2] || '',
    targetName: match?.[1] || value.title,
    isPrimary: false,
  }
}
function openStockPicker() {
  stockSelection.value = selectedStocks.value.map((target) => ({ ...target }))
  stockKeyword.value = ''
  showStockPicker.value = true
  findStocks()
}
function closeStockPicker() {
  showStockPicker.value = false
}
function isStockSelected(value: { id: number; title: string }) {
  return stockSelection.value.some((target) => target.targetCode === stockTarget(value).targetCode)
}
function chooseStock(value: { id: number; title: string }) {
  const target = stockTarget(value)
  if (isStockSelected(value))
    stockSelection.value = stockSelection.value.filter(
      (item) => item.targetCode !== target.targetCode,
    )
  else stockSelection.value.push(target)
}
function confirmStockSelection() {
  const current = form.value.targets.filter((target) => target.targetTypeCode !== 'STOCK')
  form.value.targets = [...current, ...stockSelection.value]
  if (form.value.targets.length && !form.value.targets.some((target) => target.isPrimary))
    form.value.targets[0]!.isPrimary = true
  closeStockPicker()
}
function removeTarget(index: number) {
  const wasPrimary = form.value.targets[index]?.isPrimary
  form.value.targets.splice(index, 1)
  if (wasPrimary && form.value.targets.length) form.value.targets[0]!.isPrimary = true
}
async function findRelations() {
  const requestId = ++relationRequest
  const type = relationType.value
  const keyword = relationKeyword.value
  relationLoading.value = true
  try {
    const options = await aiAnalysisApi.relatedOptions(type, keyword)
    if (requestId === relationRequest)
      relationOptions.value = options.filter(
        (option) => type !== 'ANALYSIS' || option.id !== id.value,
      )
  } catch (e) {
    if (requestId === relationRequest)
      error.value = e instanceof Error ? e.message : '관련 데이터를 검색하지 못했습니다.'
  } finally {
    if (requestId === relationRequest) relationLoading.value = false
  }
}
function openRelationPicker() {
  pendingRelations.value = form.value.relations.map((relation) => ({ ...relation }))
  relationType.value = 'STRATEGY'
  relationKeyword.value = ''
  showRelationPicker.value = true
  findRelations()
}
function closeRelationPicker() {
  showRelationPicker.value = false
}
function isRelationSelected(id: number) {
  return pendingRelations.value.some(
    (relation) => relation.relationTypeCode === relationType.value && relation.relatedId === id,
  )
}
function toggleRelation(option: { id: number; title: string }) {
  if (isRelationSelected(option.id)) {
    pendingRelations.value = pendingRelations.value.filter(
      (relation) =>
        relation.relationTypeCode !== relationType.value || relation.relatedId !== option.id,
    )
  } else {
    pendingRelations.value.push({
      relationTypeCode: relationType.value,
      relatedId: option.id,
      relationNote: '',
      relatedTitle: option.title,
    })
  }
}
function confirmRelationSelection() {
  form.value.relations = [...pendingRelations.value]
  closeRelationPicker()
}
function removeRelation(relation: Relation) {
  form.value.relations = form.value.relations.filter(
    (item) =>
      item.relationTypeCode !== relation.relationTypeCode || item.relatedId !== relation.relatedId,
  )
}
function primary(index: number) {
  form.value.targets.forEach((target, i) => (target.isPrimary = i === index))
}
function addTag() {
  const value = tagCandidate.value
  if (value && form.value.tags.length < tagLimit && !form.value.tags.includes(value)) {
    form.value.tags.push(value)
  }
  tagInput.value = ''
}
function handleTagKeydown(event: KeyboardEvent) {
  if (event.isComposing) return
  if (['Enter', ',', ' '].includes(event.key)) {
    event.preventDefault()
    addTag()
  }
}
function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files || [])) {
    if (existing.value.length + pending.value.length >= 30) {
      error.value = '첨부파일은 30개까지 가능합니다.'
      break
    }
    pending.value.push(file)
  }
  input.value = ''
}
function moveExisting(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= existing.value.length) return
  ;[existing.value[index], existing.value[target]] = [
    existing.value[target]!,
    existing.value[index]!,
  ]
}
function movePending(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= pending.value.length) return
  ;[pending.value[index], pending.value[target]] = [pending.value[target]!, pending.value[index]!]
}
function addImage(file: File, key: string) {
  if (existing.value.length + pending.value.length >= 30) {
    error.value = '첨부파일은 30개까지 가능합니다.'
    removeImageNode('uploadKey', key)
    return
  }
  imageKeys.set(file, key)
  pending.value.push(file)
}
function removeImageNode(attribute: 'fileId' | 'uploadKey', value: string | number) {
  const copy = JSON.parse(JSON.stringify(richContent.value)) as JournalContent
  function clean(node: Record<string, any>) {
    if (!Array.isArray(node.content)) return
    node.content = node.content.filter(
      (child: Record<string, any>) =>
        !(child.type === 'journalImage' && String(child.attrs?.[attribute]) === String(value)),
    )
    node.content.forEach(clean)
  }
  clean(copy)
  richContent.value = copy
}
function removePending(index: number) {
  const file = pending.value[index]
  if (!file) return
  const key = imageKeys.get(file)
  if (key) removeImageNode('uploadKey', key)
  imageKeys.delete(file)
  pending.value.splice(index, 1)
}
function removeExisting(file: AnalysisFile) {
  existing.value = existing.value.filter((x) => x.fileId !== file.fileId)
  if (file.fileType === 'IMAGE') removeImageNode('fileId', file.fileId)
}
async function save() {
  const refs = collectImageReferences(richContent.value)
  pending.value = pending.value.filter(
    (file) => !imageKeys.has(file) || refs.uploadKeys.has(imageKeys.get(file)!),
  )
  existing.value = existing.value.filter(
    (file) => file.fileType !== 'IMAGE' || refs.fileIds.has(file.fileId),
  )
  const body = {
    ...form.value,
    content:
      form.value.contentFormat === 'HTML' ? editor.value?.getHtml() || '' : form.value.content,
    analyzedAt:
      form.value.analyzedAt?.length === 16 ? `${form.value.analyzedAt}:00` : form.value.analyzedAt,
    dataAsOf: form.value.dataAsOf ? `${form.value.dataAsOf}:00` : null,
    targets: form.value.targets.filter((x) => x.targetName.trim()),
    retainedFileIds: existing.value.map((x) => x.fileId),
    newFiles: pending.value.map((file, index) => ({
      uploadKey: imageKeys.get(file) || crypto.randomUUID(),
      fileType: imageKeys.has(file) ? 'IMAGE' : 'FILE',
      originalFileName: file.name,
      sortOrder: existing.value.length + index,
    })),
  } as AnalysisPayload
  if (!body.title.trim() || !body.content.trim()) {
    error.value = '제목과 분석 본문을 입력해 주세요.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const result = id.value
      ? await aiAnalysisApi.update(id.value, body, pending.value)
      : await aiAnalysisApi.create(body, pending.value)
    router.push(`/ai-analyses/${result.analysisId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '분석을 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
async function remove() {
  if (!detail.value || !confirm(`「${detail.value.title}」 분석을 삭제하시겠습니까?`)) return
  try {
    await aiAnalysisApi.delete(detail.value.analysisId)
    router.push('/ai-analyses')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}
function relationPath(x: Relation) {
  return (
    (
      {
        STRATEGY: `/strategies/${x.relatedId}`,
        JOURNAL: `/journals/${x.relatedId}`,
        ANALYSIS: `/ai-analyses/${x.relatedId}`,
      } as Record<string, string>
    )[x.relationTypeCode] || ''
  )
}
const dateTime = (value: string | null | undefined) =>
  value ? new Date(value).toLocaleString('ko-KR') : '—'
</script>
<template>
  <div class="journal-page">
    <div class="journal-page-head">
      <div>
        <p class="analysis-eyebrow">
          AI ANALYSIS <span class="analysis-eyebrow">AiAnalysisView.vue</span>
        </p>
        <h1>
          {{
            listing
              ? 'AI 분석 관리'
              : editing
                ? id
                  ? 'AI 분석 수정'
                  : 'AI 분석 등록'
                : detail?.title || 'AI 분석 상세'
          }}
        </h1>
        <span>AI 분석 결과와 투자 판단의 근거를 관리합니다.</span>
      </div>
      <button v-if="listing" class="primary" @click="router.push('/ai-analyses/new')">
        신규 등록
      </button>
      <div v-else class="head-actions">
        <button class="secondary" @click="router.push('/ai-analyses')">목록</button
        ><button
          v-if="!editing && detail"
          class="primary"
          @click="router.push(`/ai-analyses/${id}/edit`)"
        >
          수정</button
        ><button v-if="!editing && detail" class="danger" @click="remove">삭제</button>
      </div>
    </div>
    <p v-if="error" class="journal-alert">{{ error }}</p>
    <template v-if="listing">
      <section class="journal-card journal-filters account-style-search analysis-filters">
        <label>시작일<input v-model="filters.dateFrom" type="date" /></label
        ><label>종료일<input v-model="filters.dateTo" type="date" /></label>
        <label
          >유형<select v-model="filters.type">
            <option value="">전체</option>
            <option v-for="x in options(types)" :key="x.value" :value="x.value">
              {{ x.label }}
            </option>
          </select></label
        >
        <label
          >목적<select v-model="filters.purpose">
            <option value="">전체</option>
            <option v-for="x in options(purposes)" :key="x.value" :value="x.value">
              {{ x.label }}
            </option>
          </select></label
        >
        <label
          >출처<select v-model="filters.source">
            <option value="">전체</option>
            <option v-for="x in options(sources)" :key="x.value" :value="x.value">
              {{ x.label }}
            </option>
          </select></label
        >
        <label
          >검토<select v-model="filters.review">
            <option value="">전체</option>
            <option v-for="x in options(reviews)" :key="x.value" :value="x.value">
              {{ x.label }}
            </option>
          </select></label
        >
        <label>제목·본문<input v-model="filters.keyword" @keyup.enter="search" /></label
        ><label>대상명·코드<input v-model="filters.target" @keyup.enter="search" /></label
        ><label>태그<input v-model="filters.tag" @keyup.enter="search" /></label>
        <button class="primary" @click="search">조회</button
        ><button class="secondary" @click="reset">초기화</button>
      </section>
      <section class="journal-card">
        <DataTable
          :value="rows"
          :loading="loading"
          data-key="analysisId"
          lazy
          paginator
          :rows="20"
          :total-records="total"
          :first="page * 20"
          @page="onPage"
          @row-click="router.push(`/ai-analyses/${$event.data.analysisId}`)"
          table-style="min-width: 920px"
          class="account-style-table"
        >
          <Column field="analyzedAt" header="분석일시"
            ><template #body="{ data }">{{ dateTime(data.analyzedAt) }}</template></Column
          >
          <Column field="title" header="제목" /><Column header="주요 대상"
            ><template #body="{ data }">{{
              data.targets.find((x: Target) => x.isPrimary)?.targetName ||
              data.targets[0]?.targetName ||
              '—'
            }}</template></Column
          >
          <Column header="유형"
            ><template #body="{ data }">{{ label(types, data.analysisTypeCode) }}</template></Column
          ><Column header="목적"
            ><template #body="{ data }">{{
              label(purposes, data.analysisPurposeCode)
            }}</template></Column
          >
          <Column header="출처"
            ><template #body="{ data }">{{ label(sources, data.sourceCode) }}</template></Column
          ><Column header="의견"
            ><template #body="{ data }">{{ label(opinions, data.opinionCode) }}</template></Column
          ><Column header="검토"
            ><template #body="{ data }">{{
              label(reviews, data.reviewStatusCode)
            }}</template></Column
          >
          <Column header="태그"
            ><template #body="{ data }">{{
              data.tags.map((x: string) => `#${x}`).join(' ')
            }}</template></Column
          ><template #empty>분석 결과가 없습니다.</template>
        </DataTable>
      </section>
    </template>
    <p v-else-if="loading" class="journal-empty">불러오는 중입니다.</p>
    <template v-else-if="editing">
      <section class="journal-card analysis-form">
        <h2>기본 정보</h2>
        <div class="analysis-grid">
          <label>제목 *<input v-model="form.title" maxlength="300" /></label
          ><label>분석일시 *<input v-model="form.analyzedAt" type="datetime-local" /></label>
          <label
            >분석 유형 *<select v-model="form.analysisTypeCode">
              <option v-for="x in options(types)" :key="x.value" :value="x.value">
                {{ x.label }}
              </option>
            </select></label
          >
          <label
            >분석 목적<select v-model="form.analysisPurposeCode">
              <option value="">선택 안 함</option>
              <option v-for="x in options(purposes)" :key="x.value" :value="x.value">
                {{ x.label }}
              </option>
            </select></label
          >
          <label
            >출처 *<select v-model="form.sourceCode">
              <option v-for="x in options(sources)" :key="x.value" :value="x.value">
                {{ x.label }}
              </option>
            </select></label
          >
          <label>원문 링크<input v-model="form.sourceUrl" type="url" maxlength="2000" /></label>
        </div>
      </section>
      <section class="journal-card analysis-form">
        <h2>분석 대상</h2>
        <div class="analysis-stock-picker">
          <div class="analysis-stock-heading">
            <label>관련 종목</label>
            <button class="secondary" type="button" @click="openStockPicker">
              <i class="pi pi-search"></i> 종목 검색
            </button>
          </div>
          <div v-if="selectedStocks.length" class="analysis-stock-list">
            <div
              v-for="target in selectedStocks"
              :key="target.targetCode || target.targetName"
              class="analysis-stock-item"
            >
              <span
                ><strong :title="target.targetName">{{ target.targetName }}</strong
                ><small>{{ target.targetCode || '코드 없음' }}</small></span
              >
              <label
                ><input
                  :checked="target.isPrimary"
                  type="radio"
                  name="primary"
                  @change="primary(form.targets.indexOf(target))"
                />
                대표</label
              >
              <button
                type="button"
                :aria-label="`${target.targetName} 선택 해제`"
                @click="removeTarget(form.targets.indexOf(target))"
              >
                <i class="pi pi-times"></i>
              </button>
            </div>
          </div>
          <p v-else class="analysis-stock-empty">선택된 종목이 없습니다.</p>
        </div>
        <div class="analysis-section-head analysis-other-heading">
          <label>그 외 분석 대상</label>
          <button class="secondary" type="button" @click="addTarget">대상 추가</button>
        </div>
        <div
          v-for="(target, index) in form.targets"
          v-show="target.targetTypeCode !== 'STOCK'"
          :key="index"
          class="analysis-inline"
        >
          <select v-model="target.targetTypeCode">
            <option
              v-for="x in options(targetTypes).filter((item) => item.value !== 'STOCK')"
              :key="x.value"
              :value="x.value"
            >
              {{ x.label }}
            </option></select
          ><input v-model="target.targetCode" placeholder="코드" maxlength="100" /><input
            v-model="target.targetName"
            placeholder="대상명"
            maxlength="200"
          /><label
            ><input
              :checked="target.isPrimary"
              type="radio"
              name="primary"
              @change="primary(index)"
            />
            대표</label
          ><button class="danger" type="button" @click="removeTarget(index)">제거</button>
        </div>
      </section>
      <section class="journal-card analysis-form">
        <h2>분석 내용</h2>
        <label>본문 * <small>ChatGPT·Claude의 서식 있는 내용을 붙여넣을 수 있습니다.</small></label>
        <RichTextEditor
          v-if="form.contentFormat === 'HTML'"
          ref="editor"
          v-model="richContent"
          :images-enabled="true"
          :analysis-id="id || undefined"
          @image-selected="addImage"
        />
        <textarea v-else v-model="form.content" class="analysis-editor" rows="14" />
        <div class="analysis-grid">
          <label>요약<textarea v-model="form.summary" rows="3" /></label
          ><label>종합 판단<textarea v-model="form.conclusion" rows="3" /></label
          ><label>주요 위험 요인<textarea v-model="form.keyRisks" rows="3" /></label
          ><label
            >투자 의견<select v-model="form.opinionCode">
              <option value="">선택 안 함</option>
              <option v-for="x in options(opinions)" :key="x.value" :value="x.value">
                {{ x.label }}
              </option>
            </select></label
          ><label>데이터 기준 시점<input v-model="form.dataAsOf" type="datetime-local" /></label>
        </div>
      </section>
      <section class="journal-card analysis-form">
        <h2>연계 및 관리</h2>
        <div class="analysis-tag-section">
          <div class="analysis-tag-heading">
            <label for="analysis-tag-input">해시태그 <span>(최대 10개 등록 가능)</span></label>
            <span>{{ form.tags.length }} / {{ tagLimit }}</span>
          </div>
          <div class="analysis-tag-composer">
            <div class="analysis-tag-field">
              <span v-for="tag in form.tags" :key="tag" class="analysis-tag-chip">
                #{{ tag }}
                <button
                  type="button"
                  :aria-label="`${tag} 태그 제거`"
                  @click="form.tags = form.tags.filter((x) => x !== tag)"
                >
                  <i class="pi pi-times"></i>
                </button>
              </span>
              <input
                id="analysis-tag-input"
                v-model="tagInput"
                :disabled="form.tags.length >= tagLimit"
                :placeholder="form.tags.length ? '' : '#해시태그 입력'"
                autocomplete="off"
                @keydown="handleTagKeydown"
              />
              <button
                v-if="tagInput"
                type="button"
                class="analysis-tag-clear"
                aria-label="입력 내용 지우기"
                @click="tagInput = ''"
              >
                <i class="pi pi-times-circle"></i>
              </button>
            </div>
            <div v-if="canAddTag" class="analysis-tag-suggestion">
              <button type="button" @click="addTag">
                #{{ tagCandidate }} <span>태그 등록</span>
              </button>
            </div>
          </div>
          <p class="analysis-tag-help">Enter, 쉼표(,) 또는 Space 키를 누르면 태그가 등록됩니다.</p>
        </div>
        <div class="analysis-relation-picker">
          <div class="analysis-relation-heading">
            <div>
              <h2>관련 투자 데이터</h2>
              <p>투자전략, 투자일지 등과 연결할 항목을 복수 선택할 수 있습니다.</p>
            </div>
            <button class="secondary" type="button" @click="openRelationPicker">
              <i class="pi pi-search"></i> 관련 데이터 검색
            </button>
          </div>
          <div v-if="form.relations.length" class="analysis-relation-list">
            <div
              v-for="relation in form.relations"
              :key="`${relation.relationTypeCode}-${relation.relatedId}`"
              class="analysis-relation-item"
            >
              <span
                ><strong>{{
                  relation.relatedTitle ||
                  `${label(relationTypes, relation.relationTypeCode)} #${relation.relatedId}`
                }}</strong
                ><small>{{ label(relationTypes, relation.relationTypeCode) }}</small></span
              >
              <button
                type="button"
                :aria-label="`${relation.relatedTitle || relation.relatedId} 연결 해제`"
                @click="removeRelation(relation)"
              >
                <i class="pi pi-times"></i>
              </button>
            </div>
          </div>
          <p v-else class="analysis-relation-empty">연결된 투자 데이터가 없습니다.</p>
        </div>
        <div class="analysis-grid">
          <label
            >검토 상태<select v-model="form.reviewStatusCode">
              <option v-for="x in options(reviews)" :key="x.value" :value="x.value">
                {{ x.label }}
              </option>
            </select></label
          ><label>사용자 검토 의견<textarea v-model="form.userReview" rows="3" /></label>
        </div>
      </section>
      <section class="journal-card analysis-form">
        <div class="attachments">
          <div class="attachments-head">
            <div>
              <h2>첨부파일</h2>
              <p>파일당 25MB, 최대 30개</p>
            </div>
            <label class="file-picker"
              ><i class="pi pi-paperclip"></i> 파일 첨부<input
                type="file"
                multiple
                @change="selectFiles"
            /></label>
          </div>
          <ul class="file-list">
            <li v-for="(file, index) in existing" :key="`old-${file.fileId}`">
              <span
                >{{ file.originalFileName
                }}<small
                  >{{ formatBytes(file.fileSize) }} · 기존{{
                    file.fileType === 'IMAGE' ? ' 본문 이미지' : ''
                  }}</small
                ></span
              >
              <div>
                <button
                  type="button"
                  class="icon"
                  aria-label="위로"
                  :disabled="index === 0"
                  @click="moveExisting(index, -1)"
                >
                  ↑</button
                ><button
                  type="button"
                  class="icon"
                  aria-label="아래로"
                  :disabled="index === existing.length - 1"
                  @click="moveExisting(index, 1)"
                >
                  ↓</button
                ><button type="button" class="danger" @click="removeExisting(file)">제거</button>
              </div>
            </li>
            <li v-for="(file, index) in pending" :key="pendingFileKey(file)">
              <span
                >{{ file.name
                }}<small
                  >{{ formatBytes(file.size) }} ·
                  {{ imageKeys.has(file) ? '본문 이미지' : '신규 파일' }}</small
                ></span
              >
              <div>
                <button
                  type="button"
                  class="icon"
                  aria-label="위로"
                  :disabled="index === 0"
                  @click="movePending(index, -1)"
                >
                  ↑</button
                ><button
                  type="button"
                  class="icon"
                  aria-label="아래로"
                  :disabled="index === pending.length - 1"
                  @click="movePending(index, 1)"
                >
                  ↓</button
                ><button type="button" class="danger" @click="removePending(index)">제거</button>
              </div>
            </li>
            <li v-if="!existing.length && !pending.length" class="journal-empty">
              첨부파일이 없습니다.
            </li>
          </ul>
        </div>
      </section>
      <div class="form-actions">
        <button class="secondary" @click="router.push(id ? `/ai-analyses/${id}` : '/ai-analyses')">
          취소</button
        ><button class="primary" :disabled="saving" @click="save">
          {{ saving ? '저장 중...' : '저장' }}
        </button>
      </div>
    </template>
    <template v-else-if="detail"
      ><section class="journal-card analysis-form">
        <div class="analysis-grid">
          <p><b>분석일시</b><br />{{ dateTime(detail.analyzedAt) }}</p>
          <p>
            <b>유형·목적</b><br />{{ label(types, detail.analysisTypeCode) }} ·
            {{ label(purposes, detail.analysisPurposeCode) }}
          </p>
          <p><b>출처</b><br />{{ label(sources, detail.sourceCode) }}</p>
          <p>
            <b>투자 의견·검토</b><br />{{ label(opinions, detail.opinionCode) }} ·
            {{ label(reviews, detail.reviewStatusCode) }}
          </p>
        </div>
        <p v-if="detail.sourceUrl">
          <a :href="detail.sourceUrl" target="_blank" rel="noopener noreferrer">원문 링크</a>
        </p>
      </section>
      <section v-if="detail.targets.length" class="journal-card analysis-form">
        <h2>분석 대상</h2>
        <p v-for="target in detail.targets" :key="target.targetName">
          {{ target.isPrimary ? '★ ' : '' }}{{ label(targetTypes, target.targetTypeCode) }} ·
          {{ target.targetName }} {{ target.targetCode }}
        </p>
      </section>
      <section class="journal-card analysis-form">
        <h2>분석 본문</h2>
        <div
          v-if="detail.contentFormat === 'HTML'"
          class="analysis-content"
          v-html="detail.content"
        ></div>
        <pre v-else class="analysis-content">{{ detail.content }}</pre>
      </section>
      <section class="journal-card analysis-form">
        <h2>요약 및 판단</h2>
        <p v-if="detail.summary"><b>요약</b><br />{{ detail.summary }}</p>
        <p v-if="detail.conclusion"><b>종합 판단</b><br />{{ detail.conclusion }}</p>
        <p v-if="detail.keyRisks"><b>주요 위험</b><br />{{ detail.keyRisks }}</p>
        <p v-if="detail.userReview"><b>사용자 검토 의견</b><br />{{ detail.userReview }}</p>
      </section>
      <section
        v-if="detail.tags.length || detail.relations.length"
        class="journal-card analysis-form"
      >
        <h2>연계 정보</h2>
        <p>{{ detail.tags.map((x) => `#${x}`).join(' ') }}</p>
        <p
          v-for="relation in detail.relations"
          :key="`${relation.relationTypeCode}-${relation.relatedId}`"
        >
          <RouterLink v-if="relationPath(relation)" :to="relationPath(relation)"
            >{{ label(relationTypes, relation.relationTypeCode) }} ·
            {{ relation.relatedTitle || relation.relatedId }}</RouterLink
          ><span v-else
            >{{ label(relationTypes, relation.relationTypeCode) }} ·
            {{ relation.relatedTitle || relation.relatedId }}</span
          >
        </p>
      </section>
      <section v-if="detail.files.length" class="journal-card analysis-form">
        <h2>첨부파일</h2>
        <p v-for="file in detail.files" :key="file.fileId">
          <a :href="file.contentUrl">{{ file.originalFileName }}</a>
        </p>
      </section>
    </template>
    <div v-if="showStockPicker" class="analysis-stock-backdrop" @click.self="closeStockPicker">
      <section
        class="analysis-stock-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="analysis-stock-title"
      >
        <header>
          <h2 id="analysis-stock-title">분석 대상 종목 선택</h2>
          <button type="button" aria-label="종목 검색 닫기" @click="closeStockPicker">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="analysis-stock-modal-body">
          <div class="analysis-stock-search">
            <i class="pi pi-search"></i
            ><input
              v-model="stockKeyword"
              placeholder="종목코드 또는 종목명 검색"
              autofocus
              @keyup.enter="findStocks"
            /><button class="secondary" type="button" @click="findStocks">검색</button>
          </div>
          <div v-if="stockOptions.length" class="analysis-stock-options">
            <label v-for="stock in stockOptions" :key="stock.id"
              ><input
                type="checkbox"
                :checked="isStockSelected(stock)"
                @change="chooseStock(stock)"
              /><span
                ><strong>{{ stockTarget(stock).targetName }}</strong
                ><small>{{ stockTarget(stock).targetCode || '코드 없음' }}</small></span
              ></label
            >
          </div>
          <p v-else class="analysis-stock-empty">
            {{ stockLoading ? '종목을 검색하는 중입니다.' : '검색 결과가 없습니다.' }}
          </p>
        </div>
        <footer>
          <span>{{ stockSelection.length }}개 선택</span
          ><button class="secondary" type="button" @click="closeStockPicker">취소</button
          ><button class="primary" type="button" @click="confirmStockSelection">선택 완료</button>
        </footer>
      </section>
    </div>
    <div
      v-if="showRelationPicker"
      class="analysis-relation-backdrop"
      @click.self="closeRelationPicker"
    >
      <section
        class="analysis-relation-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="analysis-relation-title"
      >
        <header>
          <h2 id="analysis-relation-title">관련 투자 데이터 선택</h2>
          <button type="button" aria-label="관련 데이터 검색 닫기" @click="closeRelationPicker">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="analysis-relation-modal-body">
          <div class="analysis-relation-search">
            <select v-model="relationType" aria-label="관련 데이터 유형" @change="findRelations">
              <option v-for="item in options(relationTypes)" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
            <div>
              <i class="pi pi-search"></i
              ><input
                v-model="relationKeyword"
                placeholder="제목 또는 이름 검색"
                autofocus
                @keyup.enter="findRelations"
              />
            </div>
            <button class="secondary" type="button" @click="findRelations">검색</button>
          </div>
          <div v-if="relationOptions.length" class="analysis-relation-options">
            <label v-for="option in relationOptions" :key="`${relationType}-${option.id}`"
              ><input
                type="checkbox"
                :checked="isRelationSelected(option.id)"
                @change="toggleRelation(option)"
              /><span
                ><strong>{{ option.title }}</strong
                ><small>{{ label(relationTypes, relationType) }} · #{{ option.id }}</small></span
              ></label
            >
          </div>
          <p v-else class="analysis-relation-empty">
            {{ relationLoading ? '관련 데이터를 검색하는 중입니다.' : '검색 결과가 없습니다.' }}
          </p>
        </div>
        <footer>
          <span>{{ pendingRelations.length }}개 선택</span
          ><button class="secondary" type="button" @click="closeRelationPicker">취소</button
          ><button class="primary" type="button" @click="confirmRelationSelection">
            선택 완료
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>
<style scoped>
.analysis-filters {
  flex-wrap: wrap;
}
.analysis-form {
  margin-bottom: 16px;
  padding: 24px;
}
.analysis-form h2 {
  margin: 0 0 16px;
}
.analysis-form .attachments {
  margin-top: 0;
}
.analysis-form .attachments-head h2 {
  margin-bottom: 0;
}
.analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.analysis-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.analysis-grid input,
.analysis-grid select,
.analysis-grid textarea,
.analysis-inline input,
.analysis-inline select {
  width: 100%;
}
.analysis-inline,
.analysis-section-head,
.analysis-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
}
.analysis-inline input {
  flex: 1;
}
.analysis-section-head {
  justify-content: space-between;
}
.analysis-stock-picker,
.analysis-relation-picker {
  display: grid;
  gap: 8px;
}
.analysis-stock-heading,
.analysis-stock-item,
.analysis-stock-modal > header,
.analysis-stock-modal > footer,
.analysis-stock-search,
.analysis-relation-heading,
.analysis-relation-item,
.analysis-relation-modal > header,
.analysis-relation-modal > footer,
.analysis-relation-search {
  display: flex;
  align-items: center;
  gap: 12px;
}
.analysis-stock-heading,
.analysis-stock-item,
.analysis-stock-modal > header,
.analysis-relation-heading,
.analysis-relation-item,
.analysis-relation-modal > header {
  justify-content: space-between;
}
.analysis-stock-options,
.analysis-relation-list,
.analysis-relation-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 8px;
}
.analysis-stock-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, 260px);
  justify-content: start;
  gap: 8px;
  overflow-x: auto;
}
.analysis-stock-item span,
.analysis-stock-options span,
.analysis-relation-item span,
.analysis-relation-options span {
  display: grid;
  min-width: 0;
}
.analysis-stock-item label {
  display: flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.analysis-stock-item input,
.analysis-stock-options input,
.analysis-relation-options input {
  width: 17px;
  height: 17px;
}
.analysis-stock-item > button,
.analysis-relation-item > button {
  width: 30px;
  height: 30px;
  min-height: 30px;
  flex: 0 0 30px;
  padding: 0;
}
.analysis-other-heading {
  margin-top: 20px;
}
.analysis-stock-backdrop,
.analysis-relation-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 20px;
}
.analysis-stock-modal,
.analysis-relation-modal {
  display: flex;
  width: min(900px, 100%);
  max-height: 90vh;
  flex-direction: column;
}
.analysis-stock-modal > header,
.analysis-stock-modal > footer,
.analysis-relation-modal > header,
.analysis-relation-modal > footer {
  padding: 16px 20px;
}
.analysis-stock-modal > header h2,
.analysis-relation-modal > header h2,
.analysis-relation-heading h2 {
  margin: 0;
}
.analysis-stock-modal > footer,
.analysis-relation-modal > footer {
  justify-content: flex-end;
}
.analysis-stock-modal > footer span,
.analysis-relation-modal > footer span {
  margin-right: auto;
}
.analysis-stock-modal-body,
.analysis-relation-modal-body {
  overflow: auto;
  padding: 20px;
}
.analysis-stock-search,
.analysis-relation-search {
  margin-bottom: 12px;
}
.analysis-stock-search input {
  flex: 1;
  min-width: 0;
}
.analysis-stock-options,
.analysis-relation-options {
  max-height: min(420px, 50vh);
  overflow: auto;
  padding: 10px;
}
.analysis-stock-options label,
.analysis-relation-options label {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  cursor: pointer;
}
.analysis-stock-empty,
.analysis-relation-empty {
  margin: 0;
  padding: 18px;
  text-align: center;
}
.analysis-relation-heading > div {
  min-width: 0;
}
.analysis-relation-heading > button {
  flex: 0 0 auto;
}
.analysis-relation-search > div {
  position: relative;
  flex: 1;
  min-width: 0;
}
.analysis-relation-search i {
  position: absolute;
  top: 50%;
  left: 13px;
  transform: translateY(-50%);
}
.analysis-relation-search input {
  width: 100%;
  padding-left: 38px;
}
.analysis-relation-search select {
  width: 180px;
}
.analysis-relation-heading p {
  margin: 3px 0 0;
}
.analysis-editor {
  min-height: 250px;
  margin: 10px 0 20px;
  overflow-wrap: anywhere;
}
.analysis-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 720px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
  .analysis-inline {
    flex-wrap: wrap;
  }
  .analysis-relation-heading,
  .analysis-relation-search {
    flex-wrap: wrap;
  }
  .analysis-relation-heading > button,
  .analysis-relation-search > div {
    width: 100%;
  }
}
</style>
