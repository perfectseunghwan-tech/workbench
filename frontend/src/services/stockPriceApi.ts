export interface StockPrice {
  symbol: string
  lastPrice: string
  currency: string
  timestamp: string | null
}

export interface DailyClose {
  date: string
  closePrice: string
  currency: string
}

export interface DailyCloseResponse {
  symbol: string
  prices: DailyClose[]
}

async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(
      typeof body.message === 'string'
        ? body.message
        : `시세 조회에 실패했습니다. (${response.status})`,
    )
  }
  return response.json() as Promise<T>
}

export const stockPriceApi = {
  async getPrice(symbol: string): Promise<StockPrice> {
    const params = new URLSearchParams({ symbol })
    const response = await fetch(`/api/stock-prices?${params}`)
    return readResponse<StockPrice>(response)
  },
  async getDailyCloses(symbol: string, from: string, to: string): Promise<DailyCloseResponse> {
    const params = new URLSearchParams({ symbol, from, to })
    const response = await fetch(`/api/stock-prices/daily-closes?${params}`)
    return readResponse<DailyCloseResponse>(response)
  },
}
