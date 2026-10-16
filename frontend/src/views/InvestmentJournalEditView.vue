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
import JournalEditor from '../components/journal/JournalEditor.vue'
import JournalTradeTable from '../components/journal/JournalTradeTable.vue'
type Pending = { key: string; file: File; type: 'IMAGE' | 'FILE'; url?: string }
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
const loading = ref(!!id.value)
const saving = ref(false)
const error = ref('')
const dirty = ref(false)
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
          <label>일지일자 <b>*</b><input v-model="date" type="date" required /></label
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
          </div>
          <div class="strategy-checks">
            <label v-for="s in strategyOptions" :key="s.strategyId"
              ><input v-model="relatedStrategyIds" type="checkbox" :value="s.strategyId" /><span
                >{{ s.title }} <small>{{ statusLabel[s.status] }}</small></span
              ></label
            >
            <p v-if="!strategyOptions.length" class="journal-empty">연결할 투자전략이 없습니다.</p>
          </div>
        </div>
        <label class="field-title">본문</label
        ><JournalEditor
          v-model="content"
          :journal-id="id || undefined"
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
  </div>
</template>
<style scoped>
.strategy-checks {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 7px;
  margin-top: 12px;
}
.strategy-checks label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid var(--app-border);
  border-radius: 0.5rem;
}
.strategy-checks input {
  width: 17px;
  height: 17px;
}
.strategy-checks span {
  display: grid;
}
.strategy-checks small {
  color: var(--app-text-muted);
}
</style>
