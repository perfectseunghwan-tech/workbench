export interface RichContent {
  type: 'doc'
  content?: Record<string, unknown>[]
}
export interface ContentFile {
  fileId: number
  fileType: 'IMAGE' | 'FILE'
  originalFileName: string
  mimeType: string | null
  fileSize: number | null
  sortOrder: number
  createdAt: string
  contentUrl: string
}
export interface NewContentFile {
  uploadKey: string
  fileType: 'IMAGE' | 'FILE'
  originalFileName: string
  sortOrder: number
}
export interface StrategyStock {
  stockId: number
  stockCode: string
  stockName: string
  marketCode: string | null
  currencyCode: string
}
export interface StrategyLink {
  strategyId: number
  title: string
  status: string
}
export interface StrategySummary {
  strategyId: number
  strategyScope: 'PORTFOLIO' | 'THEME' | 'STOCK'
  strategyType: string | null
  investmentHorizon: string | null
  title: string
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED'
  versionNo: number
  stocks: StrategyStock[]
  latestActivityAt: string
  createdAt: string
  updatedAt: string
}
export interface StrategyPost {
  postId: number
  postType: 'NOTE' | 'ANALYSIS' | 'OPINION' | 'REVIEW' | 'CHANGE'
  title: string
  content: RichContent
  postDate: string
  createdAt: string
  updatedAt: string
  files: ContentFile[]
}
export interface StrategyHistory {
  historyId: number
  versionNo: number
  title: string
  content: RichContent
  archivedAt: string
}
export interface LinkedJournal {
  journalId: number
  journalDate: string
  journalTitle: string | null
  updatedAt: string
}
export interface RelatedTrade {
  tradeId: number
  tradeDate: string
  tradeType: 'BUY' | 'SELL'
  stockId: number
  stockCode: string
  stockName: string
  brokerageName: string
  accountName: string
  executionQuantity: number | string
  executionPrice: number | string
  executionAmount: number | string
  realizedProfitLoss: number | string | null
  tradeMemo: string | null
}
export interface StrategyDetail extends StrategySummary {
  content: RichContent
  posts: StrategyPost[]
  histories: StrategyHistory[]
  journals: LinkedJournal[]
  trades: RelatedTrade[]
  files: ContentFile[]
}
export interface StrategyPage {
  content: StrategySummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
export interface StrategyPayload {
  strategyScope: 'PORTFOLIO' | 'THEME' | 'STOCK'
  strategyType: string
  investmentHorizon: string
  title: string
  content: RichContent
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED'
  stockIds: number[]
  retainedFileIds: number[]
  newFiles: NewContentFile[]
}
export interface PostPayload {
  postType: StrategyPost['postType']
  title: string
  content: RichContent
  postDate: string
  retainedFileIds: number[]
  newFiles: NewContentFile[]
}

function form(payload: StrategyPayload | PostPayload, files: File[]) {
  const data = new FormData()
  data.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  files.forEach((file) => data.append('files', file, file.name))
  return data
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers:
      options?.body && !(options.body instanceof FormData)
        ? { 'Content-Type': 'application/json', ...options.headers }
        : options?.headers,
  })
  if (!response.ok) {
    let message = `요청 처리에 실패했습니다. (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.message === 'string') message = body.message
    } catch {}
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
export const investmentStrategyApi = {
  list(params: URLSearchParams) {
    return request<StrategyPage>(`/api/investment-strategies?${params}`)
  },
  links() {
    return request<StrategyLink[]>('/api/investment-strategies/links')
  },
  detail(id: number) {
    return request<StrategyDetail>(`/api/investment-strategies/${id}`)
  },
  create(payload: StrategyPayload, files: File[]) {
    return request<StrategyDetail>('/api/investment-strategies', {
      method: 'POST',
      body: form(payload, files),
    })
  },
  update(id: number, payload: StrategyPayload, files: File[]) {
    return request<StrategyDetail>(`/api/investment-strategies/${id}`, {
      method: 'PUT',
      body: form(payload, files),
    })
  },
  remove(id: number) {
    return request<void>(`/api/investment-strategies/${id}`, { method: 'DELETE' })
  },
  createPost(id: number, payload: PostPayload, files: File[]) {
    return request<StrategyPost>(`/api/investment-strategies/${id}/posts`, {
      method: 'POST',
      body: form(payload, files),
    })
  },
  updatePost(id: number, postId: number, payload: PostPayload, files: File[]) {
    return request<StrategyPost>(`/api/investment-strategies/${id}/posts/${postId}`, {
      method: 'PUT',
      body: form(payload, files),
    })
  },
  removePost(id: number, postId: number) {
    return request<void>(`/api/investment-strategies/${id}/posts/${postId}`, { method: 'DELETE' })
  },
  restore(id: number, historyId: number) {
    return request<StrategyDetail>(
      `/api/investment-strategies/${id}/history/${historyId}/restore`,
      { method: 'POST' },
    )
  },
  replaceJournals(id: number, journalIds: number[]) {
    return request<StrategyDetail>(`/api/investment-strategies/${id}/journals`, {
      method: 'PUT',
      body: JSON.stringify(journalIds),
    })
  },
}
export const strategyScopes = [
  {
    code: 'PORTFOLIO',
    name: '전체 투자전략',
    types: [
      ['INVESTMENT_PRINCIPLE', '투자원칙'],
      ['ASSET_ALLOCATION', '자산배분'],
      ['MARKET_RESPONSE', '시장 대응'],
      ['RISK_MANAGEMENT', '리스크 관리'],
      ['CASH_MANAGEMENT', '현금·유동성 관리'],
    ],
  },
  {
    code: 'THEME',
    name: '산업·테마 투자전략',
    types: [
      ['THEME_RESEARCH', '산업·테마 발굴 및 분석'],
      ['THEME_INVESTMENT', '산업·테마 투자계획'],
      ['THEME_TRADING', '산업·테마 매수·매도'],
    ],
  },
  {
    code: 'STOCK',
    name: '개별 종목 투자전략',
    types: [
      ['STOCK_RESEARCH', '종목 발굴 및 분석'],
      ['STOCK_TRADING', '종목 매수·매도'],
      ['POSITION_MANAGEMENT', '보유·비중 관리'],
      ['STOCK_RISK', '종목 리스크 관리'],
    ],
  },
] as const
export const strategyTypes = strategyScopes.flatMap(
  (scope) => scope.types as ReadonlyArray<readonly [string, string]>,
)
export const investmentHorizons = [
  ['SHORT', '단기'],
  ['MEDIUM', '중기'],
  ['LONG', '장기'],
  ['FLEXIBLE', '기간 무관'],
] as const
export const statusLabel: Record<string, string> = {
  DRAFT: '작성 중',
  ACTIVE: '진행 중',
  PAUSED: '보류',
  CLOSED: '종료',
}
export const postTypeLabel: Record<string, string> = {
  NOTE: '일반 메모',
  ANALYSIS: '기업 분석',
  OPINION: '투자 의견',
  REVIEW: '전략 점검',
  CHANGE: '전략 변경',
}
