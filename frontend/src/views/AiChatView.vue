<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { clearChat, sendChatMessage, type ChatMode } from '../services/chatApi'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
}

const conversationId = ref(crypto.randomUUID())
const mode = ref<ChatMode>('GENERAL')
const messages = ref<ChatMessage[]>([])
const draft = ref('')
const pending = ref(false)
const error = ref('')
const lastFailedMessage = ref('')
const history = ref<HTMLElement | null>(null)
let activeRequest: AbortController | null = null

async function scrollToBottom() {
  await nextTick()
  history.value?.scrollTo({
    top: history.value.scrollHeight,
    behavior: 'smooth',
  })
}

async function submit(message = draft.value) {
  const question = message.trim()
  if (pending.value || !question || question.length > 4000) return
  if (!lastFailedMessage.value || question !== lastFailedMessage.value) {
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'user',
      text: question,
    })
  }
  draft.value = ''
  error.value = ''
  lastFailedMessage.value = ''
  pending.value = true
  const request = new AbortController()
  activeRequest = request
  const requestConversationId = conversationId.value
  const requestMode = mode.value
  await scrollToBottom()

  try {
    const answer = await sendChatMessage(
      requestConversationId,
      question,
      requestMode,
      request.signal,
    )
    if (request.signal.aborted || requestConversationId !== conversationId.value) return
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      text: answer.answer,
    })
    await scrollToBottom()
  } catch (cause) {
    if (request.signal.aborted) return
    error.value = cause instanceof Error ? cause.message : '답변을 받지 못했습니다.'
    lastFailedMessage.value = question
  } finally {
    if (activeRequest === request) {
      activeRequest = null
      pending.value = false
    }
  }
}

function onInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void submit()
  }
}

async function startNewChat() {
  const previousId = conversationId.value
  activeRequest?.abort()
  activeRequest = null
  pending.value = false
  conversationId.value = crypto.randomUUID()
  messages.value = []
  draft.value = ''
  error.value = ''
  lastFailedMessage.value = ''
  try {
    await clearChat(previousId)
  } catch {
    // 새 대화는 새로운 ID를 사용하므로 이전 대화 정리 실패와 무관하게 진행한다.
  }
}

function selectMode(selected: ChatMode) {
  if (mode.value === selected) return
  void startNewChat()
  mode.value = selected
}

function retryLastMessage() {
  if (lastFailedMessage.value) void submit(lastFailedMessage.value)
}

onBeforeUnmount(() => activeRequest?.abort())
</script>

<template>
  <section class="chat-page" aria-label="AI 채팅">
    <div class="chat-panel">
      <header class="chat-header">
        <div>
          <h1>AI 채팅</h1>
          <p>
            {{
              mode === 'STOCK_ANALYSIS'
                ? '종목과 분석 기간을 질문해 주세요.'
                : '질문을 입력하면 OpenAI가 답변합니다.'
            }}
          </p>
        </div>
        <button class="chat-new-button" type="button" @click="startNewChat">
          <i class="pi pi-plus" aria-hidden="true"></i> 새 대화
        </button>
      </header>

      <div class="chat-modes" role="group" aria-label="채팅 모드">
        <button type="button" :aria-pressed="mode === 'GENERAL'" @click="selectMode('GENERAL')">
          일반 채팅
        </button>
        <button
          type="button"
          :aria-pressed="mode === 'STOCK_ANALYSIS'"
          @click="selectMode('STOCK_ANALYSIS')"
        >
          주식분석
        </button>
      </div>

      <div ref="history" class="chat-history" role="log" aria-label="대화 내용" aria-live="polite">
        <div v-if="messages.length === 0" class="chat-empty">
          <i class="pi pi-comments" aria-hidden="true"></i>
          <h2>{{ mode === 'STOCK_ANALYSIS' ? '종목을 분석해 보세요' : '무엇이든 물어보세요' }}</h2>
          <p>
            {{
              mode === 'STOCK_ANALYSIS'
                ? '종목과 분석 기간을 질문하면 가능한 범위에서 분석합니다.'
                : '입력한 질문과 답변은 이 화면에 표시됩니다.'
            }}
          </p>
        </div>
        <div
          v-for="message in messages"
          :key="message.id"
          class="chat-row"
          :class="{ 'chat-row-user': message.role === 'user' }"
        >
          <div class="chat-bubble">
            <span class="chat-speaker">{{ message.role === 'user' ? '나' : 'AI' }}</span>
            <p>{{ message.text }}</p>
          </div>
        </div>
        <div v-if="pending" class="chat-row">
          <div class="chat-bubble chat-waiting" role="status">답변을 작성하고 있습니다…</div>
        </div>
      </div>

      <div class="chat-composer">
        <div v-if="error" class="chat-error" role="alert">
          <span>{{ error }}</span>
          <button v-if="lastFailedMessage" type="button" @click="retryLastMessage">
            다시 시도
          </button>
        </div>
        <form @submit.prevent="submit()">
          <label class="chat-input-label" for="chat-question">질문</label>
          <textarea
            id="chat-question"
            v-model="draft"
            :disabled="pending"
            maxlength="4000"
            rows="3"
            :placeholder="
              mode === 'STOCK_ANALYSIS'
                ? '예: 삼성전자 향후 한 달 전망을 분석해 줘'
                : '질문을 입력하세요'
            "
            @keydown="onInputKeydown"
          ></textarea>
          <div class="chat-actions">
            <span>Enter로 전송 · Shift+Enter로 줄바꿈</span>
            <button class="chat-send-button" type="submit" :disabled="pending || !draft.trim()">
              <i class="pi pi-send" aria-hidden="true"></i> 전송
            </button>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-page {
  min-height: calc(100vh - 64px);
  padding: 24px;
  background: var(--app-canvas);
}
.chat-panel {
  display: flex;
  width: min(100%, 960px);
  min-height: calc(100vh - 112px);
  flex-direction: column;
  margin: 0 auto;
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow);
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 26px;
  border-bottom: 1px solid var(--app-border);
}
.chat-header h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
}
.chat-header p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}
.chat-modes {
  display: flex;
  gap: 8px;
  padding: 12px 26px;
  border-bottom: 1px solid var(--app-border);
}
.chat-history {
  flex: 1;
  min-height: 320px;
  max-height: calc(100vh - 370px);
  overflow-y: auto;
  padding: 26px;
}
.chat-empty {
  display: grid;
  min-height: 300px;
  place-content: center;
  color: var(--app-text-muted);
  text-align: center;
}
.chat-empty i {
  color: var(--app-primary-500);
  font-size: 30px;
}
.chat-empty h2 {
  margin: 14px 0 4px;
  color: var(--app-text);
  font-size: 18px;
}
.chat-empty p {
  margin: 0;
  font-size: 13px;
}
.chat-row {
  display: flex;
  margin-bottom: 18px;
}
.chat-row-user {
  justify-content: flex-end;
}
.chat-bubble {
  max-width: min(85%, 650px);
  padding: 14px 17px;
  color: var(--app-text);
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border);
}
.chat-row-user .chat-bubble {
  color: var(--app-primary-800);
  background: var(--app-primary-50);
  border-color: var(--app-primary-100);
}
.chat-speaker {
  display: block;
  margin-bottom: 5px;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}
.chat-bubble p {
  margin: 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.chat-waiting {
  color: var(--app-text-muted);
  font-size: 13px;
}
.chat-composer {
  padding: 18px 26px 22px;
  border-top: 1px solid var(--app-border);
}
.chat-input-label {
  display: block;
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 700;
}
.chat-composer textarea {
  resize: vertical;
}
.chat-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 11px;
}
.chat-actions span {
  color: var(--app-text-subtle);
  font-size: 12px;
}
.chat-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px 12px;
  color: var(--app-danger);
  background: var(--app-danger-soft);
  font-size: 13px;
}
@media (max-width: 760px) {
  .chat-page {
    padding: 0;
  }
  .chat-panel {
    min-height: calc(100vh - 64px);
    border: 0;
  }
  .chat-header,
  .chat-modes,
  .chat-composer {
    padding-inline: 16px;
  }
  .chat-history {
    padding: 18px 16px;
  }
  .chat-actions span {
    font-size: 10px;
  }
}
</style>
