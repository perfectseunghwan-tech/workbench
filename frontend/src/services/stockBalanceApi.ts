import type { DecimalValue } from './stockTradeApi'

export interface Balance {
  balanceId: number
  accountId: number
  brokerageId: number
  brokerageName: string
  accountName: string
  stockId: number
  stockCode: string
  stockName: string
  currencyCode: string
  balanceQuantity: DecimalValue
  currentPrice: DecimalValue | null
  purchaseAmount: DecimalValue | null
  purchasePrice: DecimalValue | null
  evaluationAmount: DecimalValue | null
  evaluationProfitLoss: DecimalValue | null
  returnRate: DecimalValue | null
  balanceUpdatedAt: string
}

export type BalancePayload = Pick<Balance, 'accountId' | 'stockId'> & {
  balanceQuantity: string
  currentPrice: string | null
  purchaseAmount: string | null
  purchasePrice: string | null
  evaluationAmount: string | null
  evaluationProfitLoss: string | null
  returnRate: string | null
}

export interface SnapshotHeader {
  snapshotId: number
  accountId: number
  snapshotAt: string
}

export interface ApplicationGroup {
  accountId: number
  brokerageId: number
  brokerageName: string
  accountName: string
  stockId: number
  stockCode: string
  stockName: string
  currencyCode: string
  buyQuantity: DecimalValue
  sellQuantity: DecimalValue
  currentQuantity: DecimalValue
  expectedQuantity: DecimalValue
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: options?.body ? { 'Content-Type': 'application/json' } : undefined,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw new Error(
      typeof body.message === 'string' ? body.message : `요청에 실패했습니다. (${response.status})`,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const stockBalanceApi = {
  list(params: URLSearchParams) {
    return request<Balance[]>(`/stock-balances?${params}`)
  },
  save(payload: BalancePayload) {
    return request<Balance>('/stock-balances', {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  delete(balanceId: number) {
    return request<void>(`/stock-balances/${balanceId}`, { method: 'DELETE' })
  },
  snapshots(accountId: number) {
    return request<SnapshotHeader[]>(`/stock-balances/snapshots?accountId=${accountId}`)
  },
  snapshot(snapshotId: number) {
    return request<{ header: SnapshotHeader; details: Balance[] }>(
      `/stock-balances/snapshots/${snapshotId}`,
    )
  },
  preview(tradeIds: number[]) {
    return request<ApplicationGroup[]>('/stock-balance-applications/preview', {
      method: 'POST',
      body: JSON.stringify({ tradeIds }),
    })
  },
  apply(tradeIds: number[]) {
    return request<ApplicationGroup[]>('/stock-balance-applications', {
      method: 'POST',
      body: JSON.stringify({ tradeIds }),
    })
  },
}
