<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  investmentStrategyApi,
  postTypeLabel,
  statusLabel,
  strategyCategories,
  type PostPayload,
  type RichContent,
  type StrategyDetail,
  type StrategyHistory,
  type StrategyPost,
} from '../services/investmentStrategyApi'
import { investmentJournalApi, type JournalSummary } from '../services/investmentJournalApi'
import StrategyEditor from '../components/strategy/StrategyEditor.vue'
const route = useRoute(),
  router = useRouter(),
  id = Number(route.params.strategyId),
  strategy = ref<StrategyDetail>(),
  loading = ref(true),
  error = ref(''),
  tab = ref<'posts' | 'trades' | 'journals' | 'history'>('posts'),
  showPost = ref(false),
  editingPostId = ref<number | null>(null),
  postType = ref<PostPayload['postType']>('NOTE'),
  postTitle = ref(''),
  postDate = ref(new Date().toISOString().slice(0, 10)),
  postContent = ref<RichContent>({ type: 'doc', content: [] }),
  saving = ref(false),
  historyPreview = ref<StrategyHistory>(),
  showJournals = ref(false),
  allJournals = ref<JournalSummary[]>([]),
  selectedJournals = ref<number[]>([])
const categoryName = (c: string) => strategyCategories.find((x) => x[0] === c)?.[1] || c
const money = (v: number | string | null) => (v == null ? '—' : Number(v).toLocaleString('ko-KR'))
const dateTime = (v: string) => new Date(v).toLocaleString('ko-KR')
const currentJournalIds = computed(() => strategy.value?.journals.map((x) => x.journalId) || [])
async function load() {
  loading.value = true
  try {
    strategy.value = await investmentStrategyApi.detail(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '투자전략을 불러오지 못했습니다.'
  } finally {
    loading.value = false
  }
}
function openPost(post?: StrategyPost) {
  editingPostId.value = post?.postId || null
  postType.value = post?.postType || 'NOTE'
  postTitle.value = post?.title || ''
  postDate.value = post?.postDate || new Date().toISOString().slice(0, 10)
  postContent.value = post?.content || { type: 'doc', content: [] }
  showPost.value = true
}
async function savePost() {
  if (!postTitle.value.trim()) {
    error.value = '게시물 제목을 입력해 주세요.'
    return
  }
  saving.value = true
  try {
    const p: PostPayload = {
      postType: postType.value,
      title: postTitle.value.trim(),
      postDate: postDate.value,
      content: postContent.value,
    }
    if (editingPostId.value) await investmentStrategyApi.updatePost(id, editingPostId.value, p)
    else await investmentStrategyApi.createPost(id, p)
    showPost.value = false
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '게시물을 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
async function removePost(post: StrategyPost) {
  if (!confirm(`'${post.title}' 게시물을 삭제하시겠습니까?`)) return
  try {
    await investmentStrategyApi.removePost(id, post.postId)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}
async function restore(h: StrategyHistory) {
  if (!confirm(`V${h.versionNo} 본문으로 복원하시겠습니까? 현재 본문은 변경이력에 보존됩니다.`))
    return
  try {
    await investmentStrategyApi.restore(id, h.historyId)
    historyPreview.value = undefined
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '복원하지 못했습니다.'
  }
}
async function removeStrategy() {
  if (!strategy.value || !confirm(`'${strategy.value.title}' 전략을 삭제하시겠습니까?`)) return
  try {
    await investmentStrategyApi.remove(id)
    router.push('/strategies')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '삭제하지 못했습니다.'
  }
}
async function openJournalPicker() {
  try {
    const r = await investmentJournalApi.getJournals(
      new URLSearchParams({ page: '0', size: '100' }),
    )
    allJournals.value = r.content
    selectedJournals.value = [...currentJournalIds.value]
    showJournals.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '투자일지를 불러오지 못했습니다.'
  }
}
async function saveJournals() {
  saving.value = true
  try {
    strategy.value = await investmentStrategyApi.replaceJournals(id, selectedJournals.value)
    showJournals.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : '연결을 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<template>
  <div class="strategy-page">
    <p v-if="loading" class="empty">투자전략을 불러오는 중입니다.</p>
    <p v-else-if="error && !strategy" class="strategy-alert">{{ error }}</p>
    <template v-else-if="strategy"
      ><header class="strategy-head detail-head">
        <div>
          <p>{{ strategy.strategyScope === 'STOCK' ? 'STOCK STRATEGY' : 'GENERAL STRATEGY' }}</p>
          <h1>{{ strategy.title }}</h1>
          <div class="meta">
            <span class="status" :class="strategy.status.toLowerCase()">{{
              statusLabel[strategy.status]
            }}</span
            ><span>현재 본문 V{{ strategy.versionNo }}</span
            ><span>최종 수정 {{ dateTime(strategy.updatedAt) }}</span>
          </div>
        </div>
        <div class="actions">
          <button class="secondary" @click="router.push('/strategies')">목록</button
          ><button class="primary" @click="router.push(`/strategies/${id}/edit`)">본문 수정</button
          ><button class="danger" @click="removeStrategy">삭제</button>
        </div>
      </header>
      <p v-if="error" class="strategy-alert">{{ error }}</p>
      <section class="strategy-card overview">
        <div class="chips">
          <span v-for="code in strategy.categoryCodes" :key="code">{{ categoryName(code) }}</span>
        </div>
        <div class="linked-stocks">
          <b>관련 종목</b><span v-if="!strategy.stocks.length">등록된 종목이 없습니다.</span
          ><span v-for="s in strategy.stocks" :key="s.stockId"
            >{{ s.stockName }} ({{ s.stockCode }})</span
          >
        </div>
      </section>
      <section class="strategy-card content">
        <header>
          <div>
            <h2>현재 투자전략</h2>
            <p>현재 유효한 투자 근거와 실행 계획입니다.</p>
          </div>
          <button class="secondary" @click="router.push(`/strategies/${id}/edit`)">수정</button>
        </header>
        <StrategyEditor :model-value="strategy.content" readonly />
      </section>
      <nav class="strategy-tabs">
        <button :class="{ active: tab === 'posts' }" @click="tab = 'posts'">
          업데이트 <b>{{ strategy.posts.length }}</b></button
        ><button :class="{ active: tab === 'trades' }" @click="tab = 'trades'">
          종목·거래 <b>{{ strategy.trades.length }}</b></button
        ><button :class="{ active: tab === 'journals' }" @click="tab = 'journals'">
          투자일지 <b>{{ strategy.journals.length }}</b></button
        ><button :class="{ active: tab === 'history' }" @click="tab = 'history'">
          변경이력 <b>{{ strategy.histories.length }}</b>
        </button>
      </nav>
      <section v-if="tab === 'posts'" class="strategy-card">
        <header>
          <div>
            <h2>추가 분석 및 의견</h2>
            <p>시간에 따른 분석과 판단을 최신순으로 기록합니다.</p>
          </div>
          <button class="primary" @click="openPost()">
            <i class="pi pi-plus" /> 업데이트 작성
          </button>
        </header>
        <div v-if="!strategy.posts.length" class="empty">작성된 업데이트가 없습니다.</div>
        <article v-for="post in strategy.posts" :key="post.postId" class="post">
          <header>
            <div>
              <span class="type">{{ postTypeLabel[post.postType] }}</span
              ><time>{{ post.postDate }}</time>
              <h3>{{ post.title }}</h3>
            </div>
            <div class="actions">
              <button class="secondary" @click="openPost(post)">수정</button
              ><button class="danger" @click="removePost(post)">삭제</button>
            </div>
          </header>
          <StrategyEditor :model-value="post.content" readonly />
        </article>
      </section>
      <section v-else-if="tab === 'trades'" class="strategy-card">
        <header>
          <div>
            <h2>관련 종목의 매매거래</h2>
            <p>전략에 연결된 종목을 기준으로 조회한 거래입니다.</p>
          </div>
        </header>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>거래일</th>
                <th>종목</th>
                <th>구분</th>
                <th>계좌</th>
                <th>수량</th>
                <th>체결가</th>
                <th>거래금액</th>
                <th>손익</th>
                <th>매매 이유</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in strategy.trades" :key="t.tradeId">
                <td>{{ t.tradeDate }}</td>
                <td>
                  {{ t.stockName }}<small>{{ t.stockCode }}</small>
                </td>
                <td>
                  <span class="trade" :class="t.tradeType.toLowerCase()">{{
                    t.tradeType === 'BUY' ? '매수' : '매도'
                  }}</span>
                </td>
                <td>{{ t.brokerageName }} · {{ t.accountName }}</td>
                <td class="number">{{ money(t.executionQuantity) }}</td>
                <td class="number">{{ money(t.executionPrice) }}</td>
                <td class="number">{{ money(t.executionAmount) }}</td>
                <td class="number">{{ money(t.realizedProfitLoss) }}</td>
                <td>{{ t.tradeMemo || '—' }}</td>
              </tr>
              <tr v-if="!strategy.trades.length">
                <td colspan="9" class="empty">관련 거래가 없습니다.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section v-else-if="tab === 'journals'" class="strategy-card">
        <header>
          <div>
            <h2>연결된 투자일지</h2>
            <p>이 전략과 직접 연결된 투자일지입니다.</p>
          </div>
          <button class="primary" @click="openJournalPicker">일지 연결 관리</button>
        </header>
        <div class="journal-links">
          <button
            v-for="j in strategy.journals"
            :key="j.journalId"
            @click="router.push(`/journals/${j.journalId}`)"
          >
            <time>{{ j.journalDate }}</time
            ><strong>{{ j.journalTitle || '제목 없음' }}</strong
            ><i class="pi pi-angle-right" />
          </button>
          <div v-if="!strategy.journals.length" class="empty">연결된 투자일지가 없습니다.</div>
        </div>
      </section>
      <section v-else class="strategy-card">
        <header>
          <div>
            <h2>본문 변경이력</h2>
            <p>현재 V{{ strategy.versionNo }} · 과거 제목과 본문만 복원됩니다.</p>
          </div>
        </header>
        <div class="history-list">
          <button v-for="h in strategy.histories" :key="h.historyId" @click="historyPreview = h">
            <b>V{{ h.versionNo }}</b
            ><span>{{ h.title }}</span
            ><time>{{ dateTime(h.archivedAt) }}</time>
          </button>
          <div v-if="!strategy.histories.length" class="empty">아직 변경이력이 없습니다.</div>
        </div>
      </section></template
    >
    <div v-if="showPost" class="strategy-backdrop" @click.self="showPost = false">
      <section class="strategy-modal wide">
        <header>
          <h2>{{ editingPostId ? '업데이트 수정' : '업데이트 작성' }}</h2>
          <button @click="showPost = false"><i class="pi pi-times" /></button>
        </header>
        <div class="modal-body">
          <div class="form-grid">
            <label
              >유형<select v-model="postType">
                <option v-for="(label, key) in postTypeLabel" :key="key" :value="key">
                  {{ label }}
                </option>
              </select></label
            ><label>작성일<input v-model="postDate" type="date" /></label
            ><label class="wide">제목<input v-model="postTitle" maxlength="200" /></label>
          </div>
          <StrategyEditor v-model="postContent" />
        </div>
        <footer>
          <button class="secondary" @click="showPost = false">취소</button
          ><button class="primary" :disabled="saving" @click="savePost">저장</button>
        </footer>
      </section>
    </div>
    <div v-if="historyPreview" class="strategy-backdrop" @click.self="historyPreview = undefined">
      <section class="strategy-modal wide">
        <header>
          <div>
            <h2>V{{ historyPreview.versionNo }} · {{ historyPreview.title }}</h2>
            <small>{{ dateTime(historyPreview.archivedAt) }}</small>
          </div>
          <button @click="historyPreview = undefined"><i class="pi pi-times" /></button>
        </header>
        <div class="modal-body">
          <StrategyEditor :model-value="historyPreview.content" readonly />
        </div>
        <footer>
          <button class="secondary" @click="historyPreview = undefined">닫기</button
          ><button class="primary" @click="restore(historyPreview)">이 버전 복원</button>
        </footer>
      </section>
    </div>
    <div v-if="showJournals" class="strategy-backdrop" @click.self="showJournals = false">
      <section class="strategy-modal">
        <header>
          <h2>투자일지 연결</h2>
          <button @click="showJournals = false"><i class="pi pi-times" /></button>
        </header>
        <div class="modal-body journal-select">
          <label v-for="j in allJournals" :key="j.journalId"
            ><input v-model="selectedJournals" type="checkbox" :value="j.journalId" /><span
              ><time>{{ j.journalDate }}</time
              ><strong>{{ j.journalTitle || '제목 없음' }}</strong></span
            ></label
          >
          <div v-if="!allJournals.length" class="empty">선택할 투자일지가 없습니다.</div>
        </div>
        <footer>
          <button class="secondary" @click="showJournals = false">취소</button
          ><button class="primary" :disabled="saving" @click="saveJournals">연결 저장</button>
        </footer>
      </section>
    </div>
  </div>
</template>
<style scoped src="../assets/strategy.css"></style>
