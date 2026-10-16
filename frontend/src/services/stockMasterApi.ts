export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
export interface ManagedStock {
  stockId: number
  stockCode: string
  stockName: string
  marketCode: string | null
  currencyCode: string
  useYn: boolean
  createdAt: string
  updatedAt: string
}
export interface StockPayload {
  stockCode: string
  stockName: string
  marketCode: string | null
  currencyCode: string
  useYn: boolean
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: options?.body
      ? { 'Content-Type': 'application/json', ...options.headers }
      : options?.headers,
  })
  if (!response.ok) {
    let message = `요청 처리에 실패했습니다. (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.message === 'string') message = body.message
    } catch {
      /* default */
    }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
export const stockMasterApi = {
  getStocks(params: URLSearchParams) {
    return request<PageResponse<ManagedStock>>(`/api/master/stocks?${params}`)
  },
  getStock(id: number) {
    return request<ManagedStock>(`/api/master/stocks/${id}`)
  },
  createStock(payload: StockPayload) {
    return request<ManagedStock>('/api/master/stocks', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateStock(id: number, payload: StockPayload) {
    return request<ManagedStock>(`/api/master/stocks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  updateStockUsage(id: number, useYn: boolean) {
    return request<ManagedStock>(`/api/master/stocks/${id}/usage`, {
      method: 'PATCH',
      body: JSON.stringify({ useYn }),
    })
  },
  deleteStock(id: number) {
    return request<void>(`/api/master/stocks/${id}`, { method: 'DELETE' })
  },
}
