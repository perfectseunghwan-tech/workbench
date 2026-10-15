export interface RichContent { type:'doc'; content?:Record<string,unknown>[] }
export interface StrategyStock { stockId:number; stockCode:string; stockName:string; marketCode:string|null; currencyCode:string }
export interface StrategyLink { strategyId:number; title:string; status:string }
export interface StrategySummary { strategyId:number; strategyScope:'GENERAL'|'STOCK'; categoryCodes:string[]; title:string; status:'DRAFT'|'ACTIVE'|'PAUSED'|'CLOSED'; versionNo:number; stocks:StrategyStock[]; latestActivityAt:string; createdAt:string; updatedAt:string }
export interface StrategyPost { postId:number; postType:'NOTE'|'ANALYSIS'|'OPINION'|'REVIEW'|'CHANGE'; title:string; content:RichContent; postDate:string; createdAt:string; updatedAt:string }
export interface StrategyHistory { historyId:number; versionNo:number; title:string; content:RichContent; archivedAt:string }
export interface LinkedJournal { journalId:number; journalDate:string; journalTitle:string|null; updatedAt:string }
export interface RelatedTrade { tradeId:number; tradeDate:string; tradeType:'BUY'|'SELL'; stockId:number; stockCode:string; stockName:string; brokerageName:string; accountName:string; executionQuantity:number|string; executionPrice:number|string; executionAmount:number|string; realizedProfitLoss:number|string|null; tradeMemo:string|null }
export interface StrategyDetail extends StrategySummary { content:RichContent; posts:StrategyPost[]; histories:StrategyHistory[]; journals:LinkedJournal[]; trades:RelatedTrade[] }
export interface StrategyPage { content:StrategySummary[]; totalElements:number; totalPages:number; page:number; size:number }
export interface StrategyPayload { strategyScope:'GENERAL'|'STOCK'; categoryCodes:string[]; title:string; content:RichContent; status:'DRAFT'|'ACTIVE'|'PAUSED'|'CLOSED'; stockIds:number[] }
export interface PostPayload { postType:StrategyPost['postType']; title:string; content:RichContent; postDate:string }

async function request<T>(url:string,options?:RequestInit):Promise<T>{const response=await fetch(url,{...options,headers:options?.body?{'Content-Type':'application/json',...options.headers}:options?.headers});if(!response.ok){let message=`요청 처리에 실패했습니다. (${response.status})`;try{const body=await response.json();if(typeof body.message==='string')message=body.message}catch{}throw new Error(message)}if(response.status===204)return undefined as T;return response.json() as Promise<T>}
export const investmentStrategyApi={
  list(params:URLSearchParams){return request<StrategyPage>(`/api/investment-strategies?${params}`)},links(){return request<StrategyLink[]>('/api/investment-strategies/links')},detail(id:number){return request<StrategyDetail>(`/api/investment-strategies/${id}`)},
  create(payload:StrategyPayload){return request<StrategyDetail>('/api/investment-strategies',{method:'POST',body:JSON.stringify(payload)})},update(id:number,payload:StrategyPayload){return request<StrategyDetail>(`/api/investment-strategies/${id}`,{method:'PUT',body:JSON.stringify(payload)})},remove(id:number){return request<void>(`/api/investment-strategies/${id}`,{method:'DELETE'})},
  createPost(id:number,payload:PostPayload){return request<StrategyPost>(`/api/investment-strategies/${id}/posts`,{method:'POST',body:JSON.stringify(payload)})},updatePost(id:number,postId:number,payload:PostPayload){return request<StrategyPost>(`/api/investment-strategies/${id}/posts/${postId}`,{method:'PUT',body:JSON.stringify(payload)})},removePost(id:number,postId:number){return request<void>(`/api/investment-strategies/${id}/posts/${postId}`,{method:'DELETE'})},
  restore(id:number,historyId:number){return request<StrategyDetail>(`/api/investment-strategies/${id}/history/${historyId}/restore`,{method:'POST'})},replaceJournals(id:number,journalIds:number[]){return request<StrategyDetail>(`/api/investment-strategies/${id}/journals`,{method:'PUT',body:JSON.stringify(journalIds)})},
}
export const strategyCategories=[['LONG_TERM','중장기'],['SHORT_TERM','단기'],['ASSET_ALLOCATION','자산배분'],['INDUSTRY_THEME','산업·테마'],['MARKET_RESPONSE','시장 대응'],['RISK_MANAGEMENT','리스크 관리']] as const
export const statusLabel:Record<string,string>={DRAFT:'작성 중',ACTIVE:'진행 중',PAUSED:'보류',CLOSED:'종료'}
export const postTypeLabel:Record<string,string>={NOTE:'일반 메모',ANALYSIS:'기업 분석',OPINION:'투자 의견',REVIEW:'전략 점검',CHANGE:'전략 변경'}
