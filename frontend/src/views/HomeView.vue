<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

const state = ref<'loading' | 'success' | 'error'>('loading')
const message = ref('백엔드 서버의 응답을 기다리고 있습니다.')
const checkedAt = ref('')
let controller: AbortController | undefined
const title = computed(() => ({
  loading: '서버 연결 확인 중',
  success: '연결 성공',
  error: '서버에 연결할 수 없습니다',
})[state.value])

async function checkConnection() {
  controller?.abort()
  const request = new AbortController()
  controller = request
  state.value = 'loading'
  message.value = '백엔드 서버의 응답을 기다리고 있습니다.'
  const timeout = window.setTimeout(() => request.abort(), 5000)
  try {
    const response = await fetch('/api/connection', { signal: request.signal, cache: 'no-store' })
    if (!response.ok) throw new Error('HTTP ' + response.status)
    const data = await response.json()
    if (data.status !== 'ok' || typeof data.message !== 'string') {
      throw new Error('예상하지 못한 서버 응답')
    }
    state.value = 'success'
    message.value = data.message
  } catch {
    state.value = 'error'
    message.value = '백엔드가 8080 포트에서 실행 중인지 확인한 후 다시 시도해 주세요.'
  } finally {
    window.clearTimeout(timeout)
    checkedAt.value = new Date().toLocaleTimeString('ko-KR')
  }
}

onMounted(checkConnection)
onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <main class="connection-page">
    <section class="connection-card" :aria-busy="state === 'loading'">
      <p class="eyebrow">LOCAL DEVELOPMENT</p>
      <div class="status-icon" :class="state" aria-hidden="true">
        {{ state === 'success' ? '✓' : state === 'error' ? '!' : '…' }}
      </div>
      <div role="status" aria-live="polite">
        <h1>{{ title }}</h1>
        <p class="description">{{ message }}</p>
      </div>
      <dl>
        <div><dt>연결 경로</dt><dd>Frontend → Backend</dd></div>
        <div><dt>API</dt><dd>/api/connection</dd></div>
        <div><dt>마지막 확인</dt><dd>{{ checkedAt || '확인 중' }}</dd></div>
      </dl>
      <button :disabled="state === 'loading'" @click="checkConnection">
        {{ state === 'loading' ? '연결 확인 중…' : '다시 확인하기' }}
      </button>
    </section>
  </main>
</template>
