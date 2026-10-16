export interface JournalContent {
  type: 'doc'
  content?: Record<string, unknown>[]
}
export interface NewFileManifest {
  uploadKey: string
  fileType: 'IMAGE' | 'FILE'
  originalFileName: string
  sortOrder: number
}
export interface JournalPayload {
  journalDate: string
  journalTitle: string | null
  contentFormat: 'JSON'
  journalContent: JournalContent
  retainedFileIds: number[]
  newFiles: NewFileManifest[]
  relatedStrategyIds: number[]
}
export interface JournalSummary {
  journalId: number
  journalDate: string
  journalTitle: string | null
  fileCount: number
  createdAt: string
  updatedAt: string
}
export interface JournalFile {
  fileId: number
  fileType: 'IMAGE' | 'FILE'
  originalFileName: string
  mimeType: string | null
  fileSize: number | null
  sortOrder: number
  createdAt: string
  contentUrl: string
}
export interface JournalStrategy {
  strategyId: number
  title: string
  status: string
}
export interface JournalDetail extends JournalSummary {
  contentFormat: string
  journalContent: JournalContent
  files: JournalFile[]
  relatedStrategies: JournalStrategy[]
}
export interface JournalPage {
  content: JournalSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options)
  if (!response.ok) {
    let message = `요청 처리에 실패했습니다. (${response.status})`
    try {
      const error = await response.json()
      if (typeof error.message === 'string') message = error.message
    } catch {}
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
function form(payload: JournalPayload, files: File[]) {
  const data = new FormData()
  data.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  files.forEach((file) => data.append('files', file, file.name))
  return data
}
export const investmentJournalApi = {
  getJournals(params: URLSearchParams) {
    return request<JournalPage>(`/api/investment-journals?${params}`)
  },
  getJournal(id: number) {
    return request<JournalDetail>(`/api/investment-journals/${id}`)
  },
  createJournal(payload: JournalPayload, files: File[]) {
    return request<JournalDetail>('/api/investment-journals', {
      method: 'POST',
      body: form(payload, files),
    })
  },
  updateJournal(id: number, payload: JournalPayload, files: File[]) {
    return request<JournalDetail>(`/api/investment-journals/${id}`, {
      method: 'PUT',
      body: form(payload, files),
    })
  },
  deleteJournal(id: number) {
    return request<void>(`/api/investment-journals/${id}`, { method: 'DELETE' })
  },
  getFileUrl(journalId: number, fileId: number) {
    return `/api/investment-journals/${journalId}/files/${fileId}`
  },
}
