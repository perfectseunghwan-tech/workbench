export interface ChatResponse {
  conversationId: string
  answer: string
  createdAt: string
}

export async function sendChatMessage(
  conversationId: string,
  message: string,
  signal?: AbortSignal,
): Promise<ChatResponse> {
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message }),
    signal,
  })
  if (!response.ok) {
    let errorMessage = `답변을 받지 못했습니다. (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.message === 'string') errorMessage = body.message
    } catch {
      // JSON 오류 본문이 아니면 기본 메시지를 사용한다.
    }
    throw new Error(errorMessage)
  }
  return response.json() as Promise<ChatResponse>
}

export async function clearChat(conversationId: string): Promise<void> {
  const response = await fetch(`/api/ai/chat/${encodeURIComponent(conversationId)}`, {
    method: 'DELETE',
  })
  if (!response.ok) throw new Error('이전 대화를 초기화하지 못했습니다.')
}
