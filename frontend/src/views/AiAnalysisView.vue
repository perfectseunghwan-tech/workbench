<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import RichTextEditor from '../components/RichTextEditor.vue'
import { collectImageReferences, emptyJournalContent } from '../utils/journalContent'
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
const richContent = ref<JournalContent>(emptyJournalContent())
const tagInput = ref('')
const editor = ref<{
  getHtml: () => string
  getJson: () => JournalContent
  setHtml: (html: string) => void
} | null>(null)
const relationType = ref('STRATEGY')
const relationId = ref('')
const relationKeyword = ref('')
const relationOptions = ref<{ id: number; title: string }[]>([])
const stockKeyword = ref('')
const stockOptions = ref<{ id: number; title: string }[]>([])
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
  relationId.value = ''
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
    targetTypeCode: 'STOCK',
    targetCode: '',
    targetName: '',
    isPrimary: form.value.targets.length === 0,
  })
}
async function findStocks() {
  try {
    stockOptions.value = await aiAnalysisApi.stockOptions(stockKeyword.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '종목을 검색하지 못했습니다.'
  }
}
function chooseStock(value: { id: number; title: string }) {
  const match = value.title.match(/^(.*) \(([^()]*)\)$/)
  form.value.targets.push({
    targetTypeCode: 'STOCK',
    targetCode: match?.[2] || '',
    targetName: match?.[1] || value.title,
    isPrimary: form.value.targets.length === 0,
  })
  stockOptions.value = []
  stockKeyword.value = ''
}
async function findRelations() {
  try {
    relationOptions.value = await aiAnalysisApi.relatedOptions(
      relationType.value,
      relationKeyword.value,
    )
  } catch (e) {
    error.value = e instanceof Error ? e.message : '관련 데이터를 검색하지 못했습니다.'
  }
}
function primary(index: number) {
  form.value.targets.forEach((target, i) => (target.isPrimary = i === index))
}
function addTag() {
  const value = tagInput.value.replace(/^#/, '').trim().replace(/\s+/g, ' ')
  if (value && !form.value.tags.includes(value)) form.value.tags.push(value)
  tagInput.value = ''
}
function addRelation() {
  const relatedId = Number(relationId.value)
  if (
    !Number.isSafeInteger(relatedId) ||
    relatedId <= 0 ||
    form.value.relations.some(
      (x) => x.relationTypeCode === relationType.value && x.relatedId === relatedId,
    )
  )
    return
  form.value.relations.push({ relationTypeCode: relationType.value, relatedId, relationNote: '' })
  relationId.value = ''
}
function selectFiles(event: Event) {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files || []))
    if (existing.value.length + pending.value.length < 30) pending.value.push(file)
  input.value = ''
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
      fileType: file.type.startsWith('image/') ? 'IMAGE' : 'FILE',
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
        <p>AI ANALYSIS</p>
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
        <div class="analysis-section-head">
          <h2>분석 대상</h2>
          <button class="secondary" @click="addTarget">대상 추가</button>
        </div>
        <div class="analysis-inline">
          <input
            v-model="stockKeyword"
            placeholder="종목명 또는 코드 검색"
            @keyup.enter="findStocks"
          /><button class="secondary" @click="findStocks">종목 검색</button>
        </div>
        <div class="analysis-chips">
          <button
            v-for="stock in stockOptions"
            :key="stock.id"
            class="secondary"
            @click="chooseStock(stock)"
          >
            {{ stock.title }}
          </button>
        </div>
        <div v-for="(target, index) in form.targets" :key="index" class="analysis-inline">
          <select v-model="target.targetTypeCode">
            <option v-for="x in options(targetTypes)" :key="x.value" :value="x.value">
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
          ><button class="danger" @click="form.targets.splice(index, 1)">제거</button>
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
        <div class="analysis-grid">
          <label
            >해시태그<input
              v-model="tagInput"
              placeholder="#태그 입력 후 Enter"
              @keyup.enter="addTag" /></label
          ><button class="secondary" @click="addTag">태그 추가</button>
        </div>
        <div class="analysis-chips">
          <button
            v-for="tag in form.tags"
            :key="tag"
            class="secondary"
            @click="form.tags = form.tags.filter((x) => x !== tag)"
          >
            #{{ tag }} ×
          </button>
        </div>
        <div class="analysis-inline">
          <select v-model="relationType">
            <option v-for="x in options(relationTypes)" :key="x.value" :value="x.value">
              {{ x.label }}
            </option></select
          ><input
            v-model="relationKeyword"
            placeholder="제목 검색"
            @keyup.enter="findRelations"
          /><button class="secondary" @click="findRelations">검색</button
          ><select v-model="relationId">
            <option value="">연결할 항목 선택</option>
            <option v-for="option in relationOptions" :key="option.id" :value="String(option.id)">
              {{ option.title }}
            </option></select
          ><button class="secondary" @click="addRelation">연결</button>
        </div>
        <div class="analysis-chips">
          <button
            v-for="(relation, index) in form.relations"
            :key="`${relation.relationTypeCode}-${relation.relatedId}`"
            class="secondary"
            @click="form.relations.splice(index, 1)"
          >
            {{ label(relationTypes, relation.relationTypeCode) }} #{{ relation.relatedId }} ×
          </button>
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
        <h2>첨부파일</h2>
        <input type="file" multiple @change="selectFiles" />
        <div class="analysis-chips">
          <button
            v-for="file in existing"
            :key="file.fileId"
            class="secondary"
            @click="removeExisting(file)"
          >
            {{ file.originalFileName }} ×</button
          ><button
            v-for="(file, index) in pending"
            :key="index"
            class="secondary"
            @click="removePending(index)"
          >
            {{ file.name }} ×
          </button>
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
.analysis-chips,
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
.analysis-chips {
  flex-wrap: wrap;
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
}
</style>
