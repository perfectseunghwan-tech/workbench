<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  investmentJournalApi,
  type JournalContent,
  type JournalFile,
  type NewFileManifest,
} from '../services/investmentJournalApi'
import {
  investmentStrategyApi,
  statusLabel,
  type StrategyLink,
} from '../services/investmentStrategyApi'
import {
  collectImageReferences,
  emptyJournalContent,
  formatBytes,
  localDate,
} from '../utils/journalContent'
import RichTextEditor from '../components/RichTextEditor.vue'
import JournalTradeTable from '../components/journal/JournalTradeTable.vue'
type Pending = {
  key: string
  file: File
  type: 'IMAGE' | 'FILE'
  url?: string
}
const route = useRoute()
const router = useRouter()
const id = computed(() => (route.params.journalId ? Number(route.params.journalId) : null))
const date = ref(localDate())
const title = ref('')
const content = ref<JournalContent>(emptyJournalContent())
const existing = ref<JournalFile[]>([])
const pending = ref<Pending[]>([])
const strategyOptions = ref<StrategyLink[]>([])
const relatedStrategyIds = ref<number[]>([])
const pendingRelatedStrategyIds = ref<number[]>([])
const strategyKeyword = ref('')
const showStrategyPicker = ref(false)
const loading = ref(!!id.value)
const saving = ref(false)
const error = ref('')
const dirty = ref(false)
const selectedStrategies = computed(() =>
  relatedStrategyIds.value
    .map((strategyId) =>
      strategyOptions.value.find((strategy) => strategy.strategyId === strategyId),
    )
    .filter((strategy): strategy is StrategyLink => Boolean(strategy)),
)
const filteredStrategies = computed(() => {
  const keyword = strategyKeyword.value.trim().toLowerCase()
  return strategyOptions.value.filter(
    (strategy) =>
      !keyword ||
      strategy.title.toLowerCase().includes(keyword) ||
      statusLabel[strategy.status]?.toLowerCase().includes(keyword),
  )
})
watch([date, title, content, existing, pending, relatedStrategyIds], () => (dirty.value = true), {
  deep: true,
})
onMounted(async () => {
  try {
    strategyOptions.value = await investmentStrategyApi.links()
    if (id.value) {
      const j = await investmentJournalApi.getJournal(id.value)
      date.value = j.journalDate
      title.value = j.journalTitle || ''
      content.value = j.journalContent
      existing.value = j.files
      relatedStrategyIds.value = j.relatedStrategies.map((x) => x.strategyId)
    }
    setTimeout(() => (dirty.value = false))
  } catch (e) {
    error.value = e instanceof Error ? e.message : '일지를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
})
function addImage(file: File, key: string) {
  if (pending.value.length + existing.value.length >= 30) {
    error.value = '첨부파일은 30개까지 가능합니다.'
    return
  }
  pending.value.push({ key, file, type: 'IMAGE' })
}
function openStrategyPicker() {
  pendingRelatedStrategyIds.value = [...relatedStrategyIds.value]
  strategyKeyword.value = ''
  showStrategyPicker.value = true
}
function closeStrategyPicker() {
  showStrategyPicker.value = false
}
function confirmStrategySelection() {
  relatedStrategyIds.value = [...pendingRelatedStrategyIds.value]
  closeStrategyPicker()
}
function removeStrategy(strategyId: number) {
  relatedStrategyIds.value = relatedStrategyIds.value.filter((id) => id !== strategyId)
}
function addFiles(e: Event) {
  const input = e.target as HTMLInputElement
  for (const file of Array.from(input.files || [])) {
    if (pending.value.length + existing.value.length >= 30) {
      error.value = '첨부파일은 30개까지 가능합니다.'
      break
    }
    pending.value.push({ key: crypto.randomUUID(), file, type: 'FILE' })
  }
  input.value = ''
}
function move(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= existing.value.length + pending.value.length) return
  const all = [
    ...existing.value.map((x) => ({ kind: 'old' as const, value: x })),
    ...pending.value.map((x) => ({ kind: 'new' as const, value: x })),
  ]
  ;[all[index], all[target]] = [all[target]!, all[index]!]
  existing.value = all.filter((x) => x.kind === 'old').map((x) => x.value as JournalFile)
  pending.value = all.filter((x) => x.kind === 'new').map((x) => x.value as Pending)
}
function removeImageNode(attribute: 'uploadKey' | 'fileId', value: string | number) {
  const copy = structuredClone(content.value) as any
  function clean(node: any) {
    if (Array.isArray(node.content))
      node.content = node.content
        .filter(
          (child: any) =>
            !(child.type === 'journalImage' && String(child.attrs?.[attribute]) === String(value)),
        )
        .map((child: any) => {
          clean(child)
          return child
        })
  }
  clean(copy)
  content.value = copy
}
function removeExisting(file: JournalFile) {
  if (file.fileType === 'IMAGE') removeImageNode('fileId', file.fileId)
  existing.value = existing.value.filter((x) => x.fileId !== file.fileId)
}
function removePending(item: Pending) {
  if (item.type === 'IMAGE') removeImageNode('uploadKey', item.key)
  if (item.url) URL.revokeObjectURL(item.url)
  pending.value = pending.value.filter((x) => x.key !== item.key)
}
function syncFiles() {
  const refs = collectImageReferences(content.value)
  pending.value
    .filter((x) => x.type === 'IMAGE' && !refs.uploadKeys.has(x.key))
    .forEach(removePending)
  existing.value = existing.value.filter(
    (x) => x.fileType !== 'IMAGE' || refs.fileIds.has(x.fileId),
  )
}
async function save() {
  syncFiles()
  if (!date.value) {
    error.value = '일지일자를 입력해 주세요.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const manifests: NewFileManifest[] = pending.value.map((x, i) => ({
      uploadKey: x.key,
      fileType: x.type,
      originalFileName: x.file.name,
      sortOrder: existing.value.length + i,
    }))
    const payload = {
      journalDate: date.value,
      journalTitle: title.value.trim() || null,
      contentFormat: 'JSON' as const,
      journalContent: content.value,
      retainedFileIds: existing.value.map((x) => x.fileId),
      newFiles: manifests,
      relatedStrategyIds: relatedStrategyIds.value,
    }
    const result = id.value
      ? await investmentJournalApi.updateJournal(
          id.value,
          payload,
          pending.value.map((x) => x.file),
        )
      : await investmentJournalApi.createJournal(
          payload,
          pending.value.map((x) => x.file),
        )
    dirty.value = false
    router.push(`/journals/${result.journalId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '일지를 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
function cancel() {
  if (dirty.value && !confirm('작성 중인 변경 내용을 버리시겠습니까?')) return
  dirty.value = false
  router.push(id.value ? `/journals/${id.value}` : '/journals')
}
onBeforeRouteLeave(
  () => !dirty.value || confirm('저장하지 않은 변경 내용이 있습니다. 이동하시겠습니까?'),
)
</script>
<template>
  <div class="journal-page">
    <div class="journal-page-head">
      <div>
        <p>INVESTMENT JOURNAL</p>
        <h1>{{ id ? '투자일지 수정' : '투자일지 작성' }}</h1>
        <span>투자 판단의 근거와 회고를 기록해 두세요.</span>
      </div>
    </div>
    <p v-if="error" class="journal-alert">{{ error }}</p>
    <p v-if="loading" class="journal-empty">일지를 불러오는 중입니다.</p>
    <template v-else
      ><section class="journal-card journal-form">
        <div class="form-grid">
          <label
            ><span class="required-label"><b>*</b> 일지일자</span
            ><input v-model="date" type="date" required /></label
          ><label class="grow"
            >제목 <small>선택</small
            ><input v-model="title" maxlength="200" placeholder="제목을 입력하세요"
          /></label>
        </div>
        <div class="attachments">
          <div class="attachments-head">
            <div>
              <h2>관련 투자전략</h2>
              <p>이 일지와 연결할 전략을 복수 선택할 수 있습니다.</p>
            </div>
            <button class="secondary" type="button" @click="openStrategyPicker">
              <i class="pi pi-search"></i> 투자전략 검색
            </button>
          </div>
          <div v-if="selectedStrategies.length" class="selected-strategy-list">
            <div
              v-for="strategy in selectedStrategies"
              :key="strategy.strategyId"
              class="selected-strategy-item"
            >
              <span
                ><strong>{{ strategy.title }}</strong
                ><small>{{ statusLabel[strategy.status] }}</small></span
              ><button
                type="button"
                :aria-label="`${strategy.title} 선택 해제`"
                @click="removeStrategy(strategy.strategyId)"
              >
                <i class="pi pi-times"></i>
              </button>
            </div>
          </div>
          <p v-else class="selected-strategy-empty">선택된 투자전략이 없습니다.</p>
        </div>
        <label class="field-title">본문</label
        ><RichTextEditor
          v-model="content"
          :journal-id="id || undefined"
          :images-enabled="true"
          @image-selected="addImage"
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
                @change="addFiles"
            /></label>
          </div>
          <ul class="file-list">
            <li v-for="(file, i) in existing" :key="`old-${file.fileId}`">
              <span
                >{{ file.originalFileName }}
                <small>{{ formatBytes(file.fileSize) }} · 기존</small></span
              >
              <div>
                <button type="button" class="icon" aria-label="위로" @click="move(i, -1)">↑</button
                ><button type="button" class="icon" aria-label="아래로" @click="move(i, 1)">
                  ↓</button
                ><button type="button" class="danger" @click="removeExisting(file)">제거</button>
              </div>
            </li>
            <li v-for="(item, i) in pending" :key="item.key">
              <span
                >{{ item.file.name }}
                <small
                  >{{ formatBytes(item.file.size) }} ·
                  {{ item.type === 'IMAGE' ? '본문 이미지' : '신규 파일' }}</small
                ></span
              >
              <div>
                <button
                  type="button"
                  class="icon"
                  aria-label="위로"
                  @click="move(existing.length + i, -1)"
                >
                  ↑</button
                ><button
                  type="button"
                  class="icon"
                  aria-label="아래로"
                  @click="move(existing.length + i, 1)"
                >
                  ↓</button
                ><button type="button" class="danger" @click="removePending(item)">제거</button>
              </div>
            </li>
            <li v-if="!existing.length && !pending.length" class="journal-empty">
              첨부파일이 없습니다.
            </li>
          </ul>
        </div>
        <div class="form-actions">
          <button class="secondary" :disabled="saving" @click="cancel">취소</button
          ><button class="primary" :disabled="saving" @click="save">
            {{ saving ? '저장 중...' : '저장' }}
          </button>
        </div>
      </section>
      <JournalTradeTable :date="date"
    /></template>
    <div
      v-if="showStrategyPicker"
      class="strategy-picker-backdrop"
      @click.self="closeStrategyPicker"
    >
      <section class="strategy-picker-modal" role="dialog" aria-modal="true">
        <header>
          <h2>관련 투자전략 선택</h2>
          <button type="button" aria-label="투자전략 검색 닫기" @click="closeStrategyPicker">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <div class="strategy-picker-body">
          <div class="strategy-search-field">
            <i class="pi pi-search"></i>
            <input v-model="strategyKeyword" placeholder="전략명 또는 상태 검색" autofocus />
          </div>
          <div v-if="filteredStrategies.length" class="strategy-options">
            <label
              v-for="strategy in filteredStrategies"
              :key="strategy.strategyId"
              class="strategy-option"
            >
              <input
                v-model="pendingRelatedStrategyIds"
                type="checkbox"
                :value="strategy.strategyId"
              /><span
                ><strong>{{ strategy.title }}</strong
                ><small>{{ statusLabel[strategy.status] }}</small></span
              >
            </label>
          </div>
          <p v-else class="selected-strategy-empty">
            {{ strategyOptions.length ? '검색 결과가 없습니다.' : '연결할 투자전략이 없습니다.' }}
          </p>
        </div>
        <footer>
          <span class="strategy-selection-count"
            >{{ pendingRelatedStrategyIds.length }}개 선택</span
          >
          <button class="secondary" type="button" @click="closeStrategyPicker">취소</button>
          <button class="primary" type="button" @click="confirmStrategySelection">선택 완료</button>
        </footer>
      </section>
    </div>
  </div>
</template>
<style scoped>
.selected-strategy-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.selected-strategy-item {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  background: var(--app-primary-50);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}
.selected-strategy-item span {
  display: grid;
  min-width: 0;
}
.selected-strategy-item small,
.strategy-option small {
  color: var(--app-text-muted);
}
.selected-strategy-item button {
  display: grid;
  width: 30px;
  height: 30px;
  min-height: 30px;
  flex: 0 0 30px;
  place-items: center;
  padding: 0;
  color: #64748b;
  background: #fff;
  border: 1px solid var(--app-border);
}
.selected-strategy-empty {
  margin: 12px 0 0;
  padding: 18px;
  color: var(--app-text-muted);
  background: #f8fafc;
  border: 1px dashed var(--app-border-strong);
  border-radius: var(--app-radius);
  text-align: center;
}
.strategy-picker-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(15 23 42 / 56%);
}
.strategy-picker-modal {
  display: flex;
  width: min(900px, 100%);
  max-height: 90vh;
  flex-direction: column;
  background: #fff;
  border-radius: var(--app-radius);
  box-shadow: 0 24px 70px rgb(15 23 42 / 22%);
}
.strategy-picker-modal > header,
.strategy-picker-modal > footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 18px 22px;
}
.strategy-picker-modal > header {
  justify-content: space-between;
  border-bottom: 1px solid var(--app-border);
}
.strategy-picker-modal > header h2 {
  margin: 0;
}
.strategy-picker-modal > header button {
  width: 34px;
  height: 34px;
  min-height: 34px;
  padding: 0;
  color: #64748b;
  background: #fff;
  border: 1px solid var(--app-border);
}
.strategy-picker-modal > footer {
  justify-content: flex-end;
  border-top: 1px solid var(--app-border);
}
.strategy-picker-body {
  min-height: 0;
  overflow: auto;
  padding: 22px;
}
.strategy-search-field {
  position: relative;
  margin-bottom: 12px;
}
.strategy-search-field i {
  position: absolute;
  top: 50%;
  left: 13px;
  color: var(--app-text-muted);
  transform: translateY(-50%);
}
.strategy-search-field input {
  width: 100%;
  padding-left: 38px;
}
.strategy-options {
  display: grid;
  max-height: min(420px, 50vh);
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 6px;
  overflow: auto;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}
.strategy-option {
  display: flex !important;
  align-items: center;
  gap: 7px;
  padding: 8px;
  background: #fff;
  border-radius: var(--app-radius);
}
.strategy-option input {
  width: 17px;
  height: 17px;
}
.strategy-option span {
  display: grid;
}
.strategy-selection-count {
  margin-right: auto;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}
@media (max-width: 650px) {
  .strategy-picker-backdrop {
    align-items: end;
    padding: 0;
  }
  .strategy-picker-modal {
    max-height: 94vh;
    border-radius: var(--app-radius) var(--app-radius) 0 0;
  }
}
</style>
