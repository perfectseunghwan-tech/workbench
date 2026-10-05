export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface ManagedBrokerage {
  brokerageId: number
  brokerageName: string
  useYn: boolean
  accountCount: number
  createdAt: string
  updatedAt: string
}

export interface ManagedAccount {
  accountId: number
  brokerageId: number
  brokerageName: string
  accountName: string
  accountNumber: string | null
  accountMemo: string | null
  useYn: boolean
  createdAt: string
  updatedAt: string
}

export interface BrokeragePayload {
  brokerageName: string
  useYn: boolean
}

export interface AccountPayload {
  brokerageId: number
  accountName: string
  accountNumber: string | null
  accountMemo: string | null
  useYn: boolean
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: options?.body ? { 'Content-Type': 'application/json', ...options.headers } : options?.headers,
  })
  if (!response.ok) {
    let message = `요청 처리에 실패했습니다. (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.message === 'string') message = body.message
    } catch {
      // JSON 응답이 아니면 기본 메시지를 사용한다.
    }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}

export const investmentMasterApi = {
  getBrokerages(params: URLSearchParams) {
    return request<PageResponse<ManagedBrokerage>>(`/api/master/brokerages?${params}`)
  },
  createBrokerage(payload: BrokeragePayload) {
    return request<ManagedBrokerage>('/api/master/brokerages', {
      method: 'POST', body: JSON.stringify(payload),
    })
  },
  updateBrokerage(id: number, payload: BrokeragePayload) {
    return request<ManagedBrokerage>(`/api/master/brokerages/${id}`, {
      method: 'PUT', body: JSON.stringify(payload),
    })
  },
  updateBrokerageUsage(id: number, useYn: boolean) {
    return request<ManagedBrokerage>(`/api/master/brokerages/${id}/usage`, {
      method: 'PATCH', body: JSON.stringify({ useYn }),
    })
  },
  getAccounts(params: URLSearchParams) {
    return request<PageResponse<ManagedAccount>>(`/api/master/investment-accounts?${params}`)
  },
  getAccount(id: number) {
    return request<ManagedAccount>(`/api/master/investment-accounts/${id}`)
  },
  createAccount(payload: AccountPayload) {
    return request<ManagedAccount>('/api/master/investment-accounts', {
      method: 'POST', body: JSON.stringify(payload),
    })
  },
  updateAccount(id: number, payload: AccountPayload) {
    return request<ManagedAccount>(`/api/master/investment-accounts/${id}`, {
      method: 'PUT', body: JSON.stringify(payload),
    })
  },
  updateAccountUsage(id: number, useYn: boolean) {
    return request<ManagedAccount>(`/api/master/investment-accounts/${id}/usage`, {
      method: 'PATCH', body: JSON.stringify({ useYn }),
    })
  },
}
