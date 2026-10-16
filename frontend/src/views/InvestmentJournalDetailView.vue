<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { investmentJournalApi, type JournalDetail } from '../services/investmentJournalApi'
import { formatBytes } from '../utils/journalContent'
import JournalEditor from '../components/journal/JournalEditor.vue'
import JournalTradeTable from '../components/journal/JournalTradeTable.vue'
const route = useRoute()
const router = useRouter()
const id = Number(route.params.journalId)
const journal = ref<JournalDetail>()
const loading = ref(true)
const error = ref('')
const dateTime = (v: string) => new Date(v).toLocaleString('ko-KR')
async function load() {
  try {
    journal.value = await investmentJournalApi.getJournal(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '일지를 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
async function remove() {
  if (
    !journal.value ||
    !confirm(
      `${journal.value.journalDate} · ${journal.value.journalTitle || '제목 없음'} 일지를 삭제하시겠습니까?`,
    )
  )
    return
  try {
    await investmentJournalApi.deleteJournal(id)
    router.push('/journals')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}
onMounted(load)
</script>
<template>
  <div class="journal-page">
    <p v-if="loading" class="journal-empty">일지를 불러오는 중입니다.</p>
    <p v-else-if="error" class="journal-alert">{{ error }}</p>
    <template v-else-if="journal"
      ><div class="journal-page-head">
        <div>
          <p>{{ journal.journalDate }}</p>
          <h1>{{ journal.journalTitle || '제목 없음' }}</h1>
          <span
            >작성 {{ dateTime(journal.createdAt) }} · 수정 {{ dateTime(journal.updatedAt) }}</span
          >
        </div>
        <div class="head-actions">
          <button class="secondary" @click="router.push('/journals')">목록</button
          ><button class="primary" @click="router.push(`/journals/${id}/edit`)">수정</button
          ><button class="danger" @click="remove">삭제</button>
        </div>
      </div>
      <section v-if="journal.relatedStrategies.length" class="journal-card">
        <header><h2>관련 투자전략</h2></header>
        <div class="related-strategies">
          <button
            v-for="s in journal.relatedStrategies"
            :key="s.strategyId"
            class="secondary"
            @click="router.push(`/strategies/${s.strategyId}`)"
          >
            <i class="pi pi-compass" /> {{ s.title }}
          </button>
        </div>
      </section>
      <section class="journal-card content-card">
        <JournalEditor :model-value="journal.journalContent" readonly :journal-id="id" />
      </section>
      <section v-if="journal.files.some((f) => f.fileType === 'FILE')" class="journal-card">
        <header><h2>첨부파일</h2></header>
        <ul class="file-list">
          <li v-for="file in journal.files.filter((f) => f.fileType === 'FILE')" :key="file.fileId">
            <span
              ><i class="pi pi-paperclip" /> {{ file.originalFileName }}
              <small>{{ formatBytes(file.fileSize) }}</small></span
            ><a :href="file.contentUrl">다운로드</a>
          </li>
        </ul>
      </section>
      <JournalTradeTable :date="journal.journalDate"
    /></template>
  </div>
</template>
<style scoped>
.related-strategies {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
