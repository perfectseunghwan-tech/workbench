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
  latestMemoContent: string | null
  latestMemoCreatedAt: string | null
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
  useYn: boolean
}

export interface AccountMemo {
  memoId: number
  accountId: number
  memoContent: string
  createdAt: string
}

export interface AccountMemoPayload {
  memoContent: string
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
      // JSON 응답이 아니면 기본 메시지를 사용한다.
    }
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const investmentMasterApi = {
  getBrokerages(params: URLSearchParams) {
    return request<PageResponse<ManagedBrokerage>>(`/api/master/brokerages?${params}`)
  },
  createBrokerage(payload: BrokeragePayload) {
    return request<ManagedBrokerage>('/api/master/brokerages', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateBrokerage(id: number, payload: BrokeragePayload) {
    return request<ManagedBrokerage>(`/api/master/brokerages/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  updateBrokerageUsage(id: number, useYn: boolean) {
    return request<ManagedBrokerage>(`/api/master/brokerages/${id}/usage`, {
      method: 'PATCH',
      body: JSON.stringify({ useYn }),
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
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateAccount(id: number, payload: AccountPayload) {
    return request<ManagedAccount>(`/api/master/investment-accounts/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  deleteAccount(id: number) {
    return request<void>(`/api/master/investment-accounts/${id}`, { method: 'DELETE' })
  },
  updateAccountUsage(id: number, useYn: boolean) {
    return request<ManagedAccount>(`/api/master/investment-accounts/${id}/usage`, {
      method: 'PATCH',
      body: JSON.stringify({ useYn }),
    })
  },
  getAccountMemos(accountId: number) {
    return request<AccountMemo[]>(`/api/master/investment-accounts/${accountId}/memos`)
  },
  createAccountMemo(accountId: number, payload: AccountMemoPayload) {
    return request<AccountMemo>(`/api/master/investment-accounts/${accountId}/memos`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateAccountMemo(accountId: number, memoId: number, payload: AccountMemoPayload) {
    return request<AccountMemo>(`/api/master/investment-accounts/${accountId}/memos/${memoId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  deleteAccountMemo(accountId: number, memoId: number) {
    return request<void>(`/api/master/investment-accounts/${accountId}/memos/${memoId}`, {
      method: 'DELETE',
    })
  },
}
