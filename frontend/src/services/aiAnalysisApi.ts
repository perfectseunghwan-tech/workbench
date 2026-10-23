export type Target = {
  targetTypeCode: string
  targetCode: string
  targetName: string
  isPrimary: boolean
}
export type Relation = {
  relationTypeCode: string
  relatedId: number
  relationNote: string
  relatedTitle?: string
}
export type AnalysisFile = {
  fileId: number
  fileType: string
  originalFileName: string
  fileSize: number
  contentUrl: string
}
export type AnalysisSummary = {
  analysisId: number
  title: string
  analysisTypeCode: string
  analysisPurposeCode: string
  sourceCode: string
  opinionCode: string
  reviewStatusCode: string
  analyzedAt: string
  targets: Target[]
  tags: string[]
}
export type AnalysisDetail = AnalysisSummary & {
  summary: string
  content: string
  contentFormat: string
  conclusion: string
  keyRisks: string
  sourceUrl: string
  dataAsOf: string
  userReview: string
  reviewedAt: string
  relations: Relation[]
  files: AnalysisFile[]
}
export type AnalysisPage = {
  content: AnalysisSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
export type AnalysisPayload = Omit<AnalysisDetail, 'analysisId' | 'reviewedAt' | 'files'> & {
  retainedFileIds: number[]
  newFiles: { uploadKey: string; fileType: string; originalFileName: string; sortOrder: number }[]
}
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options)
  if (!response.ok) {
    let message = `요청에 실패했습니다. (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.message === 'string') message = body.message
    } catch {}
    throw new Error(message)
  }
  return response.status === 204 ? (undefined as T) : (response.json() as Promise<T>)
}
function form(payload: AnalysisPayload, files: File[]) {
  const data = new FormData()
  data.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  files.forEach((file) => data.append('files', file, file.name))
  return data
}
export const aiAnalysisApi = {
  relatedOptions(type: string, keyword: string) {
    return request<{ id: number; title: string }[]>(
      `/api/ai-analyses/related-options?${new URLSearchParams({ type, keyword })}`,
    )
  },
  stockOptions(keyword: string) {
    return request<{ id: number; title: string }[]>(
      `/api/ai-analyses/stock-options?${new URLSearchParams({ keyword })}`,
    )
  },
  list(params: URLSearchParams) {
    return request<AnalysisPage>(`/api/ai-analyses?${params}`)
  },
  detail(id: number) {
    return request<AnalysisDetail>(`/api/ai-analyses/${id}`)
  },
  create(payload: AnalysisPayload, files: File[]) {
    return request<AnalysisDetail>('/api/ai-analyses', {
      method: 'POST',
      body: form(payload, files),
    })
  },
  update(id: number, payload: AnalysisPayload, files: File[]) {
    return request<AnalysisDetail>(`/api/ai-analyses/${id}`, {
      method: 'PUT',
      body: form(payload, files),
    })
  },
  delete(id: number) {
    return request<void>(`/api/ai-analyses/${id}`, { method: 'DELETE' })
  },
}
