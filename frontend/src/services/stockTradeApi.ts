export type DecimalValue = number | string

export interface Brokerage {
  brokerageId: number
  brokerageName: string
}

export interface InvestmentAccount {
  accountId: number
  brokerageId: number
  brokerageName: string
  accountName: string
  accountNumber: string | null
}

export interface Stock {
  stockId: number
  stockCode: string
  stockName: string
  marketCode: string | null
  currencyCode: string
}

export interface StockTrade {
  tradeId: number
  accountId: number
  brokerageId: number
  brokerageName: string
  accountName: string
  stockId: number
  stockCode: string
  stockName: string
  marketCode: string | null
  currencyCode: string
  tradeDate: string
  tradeType: 'BUY' | 'SELL'
  executionQuantity: DecimalValue
  executionPrice: DecimalValue
  executionAmount: DecimalValue
  purchasePrice: DecimalValue | null
  realizedProfitLoss: DecimalValue | null
  tradeMemo: string | null
  createdAt: string
  updatedAt: string
}

export interface TradePayload {
  accountId: number
  stockId: number
  tradeDate: string
  tradeType: 'BUY' | 'SELL'
  executionQuantity: string
  executionPrice: string
  purchasePrice: string | null
  realizedProfitLoss: string | null
  tradeMemo: string | null
}

export interface TradePage {
  content: StockTrade[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: options?.body ? { 'Content-Type': 'application/json', ...options.headers } : options?.headers,
  })

  if (!response.ok) {
    let message = `요청 처리에 실패했습니다. (${response.status})`
    try {
      const error = await response.json()
      if (typeof error.message === 'string') message = error.message
    } catch {
      // JSON 오류 본문이 아니면 기본 메시지를 사용한다.
    }
    throw new Error(message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const stockTradeApi = {
  getTrades(params: URLSearchParams) {
    return request<TradePage>(`/api/stock-trades?${params.toString()}`)
  },
  getBrokerages() {
    return request<Brokerage[]>('/api/brokerages')
  },
  getAccounts(brokerageId?: number) {
    const query = brokerageId ? `?brokerageId=${brokerageId}` : ''
    return request<InvestmentAccount[]>(`/api/investment-accounts${query}`)
  },
  getStocks() {
    return request<Stock[]>('/api/stocks')
  },
  createTrade(payload: TradePayload) {
    return request<StockTrade>('/api/stock-trades', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateTrade(tradeId: number, payload: TradePayload) {
    return request<StockTrade>(`/api/stock-trades/${tradeId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  deleteTrade(tradeId: number) {
    return request<void>(`/api/stock-trades/${tradeId}`, { method: 'DELETE' })
  },
}
