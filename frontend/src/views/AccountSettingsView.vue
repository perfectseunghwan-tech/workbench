<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  investmentMasterApi,
  type ManagedBrokerage,
  type ManagedAccount,
  type BrokeragePayload,
  type AccountPayload,
} from '@/services/investmentMasterApi'

const brokerages = ref<ManagedBrokerage[]>([])
const accounts = ref<ManagedAccount[]>([])
const selectedBrokerageId = ref<number | null>(null)
const brokerageKeyword = ref('')
const brokerageUseYn = ref('')
const accountKeyword = ref('')
const accountUseYn = ref('')
const brokeragePage = ref(0)
const brokeragePages = ref(0)
const brokerageTotal = ref(0)
const accountPage = ref(0)
const accountPages = ref(0)
const accountTotal = ref(0)
const loadingBrokerages = ref(false)
const loadingAccounts = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const showBrokerageForm = ref(false)
const showAccountForm = ref(false)
const editingBrokerageId = ref<number | null>(null)
const editingAccountId = ref<number | null>(null)
const brokerageForm = ref({ brokerageName: '', useYn: true })
const accountForm = ref({ brokerageId: 0, accountName: '', accountNumber: '', accountMemo: '', useYn: true })
const pageSize = 20

const selectedBrokerage = computed(() =>
  brokerages.value.find((item) => item.brokerageId === selectedBrokerageId.value) ?? null,
)
const activeBrokerages = computed(() => brokerages.value.filter((item) => item.useYn))

function params(page: number, keyword: string, useYn: string, brokerageId?: number) {
  const query = new URLSearchParams({ page: String(page), size: String(pageSize) })
  if (keyword.trim()) query.set('keyword', keyword.trim())
  if (useYn !== '') query.set('useYn', useYn)
  if (brokerageId) query.set('brokerageId', String(brokerageId))
  return query
}

function setNotice(message: string) {
  noticeMessage.value = message
  window.setTimeout(() => { if (noticeMessage.value === message) noticeMessage.value = '' }, 3000)
}

async function loadBrokerages(keepSelection = true) {
  loadingBrokerages.value = true
  errorMessage.value = ''
  try {
    const result = await investmentMasterApi.getBrokerages(
      params(brokeragePage.value, brokerageKeyword.value, brokerageUseYn.value),
    )
    brokerages.value = result.content
    brokeragePages.value = result.totalPages
    brokerageTotal.value = result.totalElements
    const selectedExists = brokerages.value.some((item) => item.brokerageId === selectedBrokerageId.value)
    if (!keepSelection || !selectedExists) selectedBrokerageId.value = brokerages.value[0]?.brokerageId ?? null
    await loadAccounts()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '증권사 정보를 불러오지 못했습니다.'
  } finally { loadingBrokerages.value = false }
}

async function loadAccounts() {
  if (!selectedBrokerageId.value) {
    accounts.value = []
    accountTotal.value = 0
    accountPages.value = 0
    return
  }
  loadingAccounts.value = true
  errorMessage.value = ''
  try {
    const result = await investmentMasterApi.getAccounts(
      params(accountPage.value, accountKeyword.value, accountUseYn.value, selectedBrokerageId.value),
    )
    accounts.value = result.content
    accountPages.value = result.totalPages
    accountTotal.value = result.totalElements
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '계좌 정보를 불러오지 못했습니다.'
  } finally { loadingAccounts.value = false }
}

async function selectBrokerage(id: number) {
  selectedBrokerageId.value = id
  accountPage.value = 0
  accountKeyword.value = ''
  accountUseYn.value = ''
  await loadAccounts()
}

async function searchBrokerages() { brokeragePage.value = 0; await loadBrokerages(false) }
async function searchAccounts() { accountPage.value = 0; await loadAccounts() }

function openBrokerageCreate() {
  editingBrokerageId.value = null
  brokerageForm.value = { brokerageName: '', useYn: true }
  showBrokerageForm.value = true
  errorMessage.value = ''
}

function openBrokerageEdit(item: ManagedBrokerage) {
  editingBrokerageId.value = item.brokerageId
  brokerageForm.value = { brokerageName: item.brokerageName, useYn: item.useYn }
  showBrokerageForm.value = true
  errorMessage.value = ''
}

async function saveBrokerage() {
  const name = brokerageForm.value.brokerageName.trim()
  if (!name) { errorMessage.value = '증권사명을 입력해 주세요.'; return }
  saving.value = true
  errorMessage.value = ''
  const payload: BrokeragePayload = { brokerageName: name, useYn: brokerageForm.value.useYn }
  try {
    const saved = editingBrokerageId.value === null
      ? await investmentMasterApi.createBrokerage(payload)
      : await investmentMasterApi.updateBrokerage(editingBrokerageId.value, payload)
    selectedBrokerageId.value = saved.brokerageId
    showBrokerageForm.value = false
    setNotice(editingBrokerageId.value === null ? '증권사가 등록되었습니다.' : '증권사가 수정되었습니다.')
    await loadBrokerages()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '증권사를 저장하지 못했습니다.'
  } finally { saving.value = false }
}

async function toggleBrokerage(item: ManagedBrokerage) {
  if (item.useYn && !window.confirm('증권사를 사용 중지하면 거래 등록 화면에서 하위 계좌가 숨겨집니다. 계속할까요?')) return
  try {
    await investmentMasterApi.updateBrokerageUsage(item.brokerageId, !item.useYn)
    setNotice(item.useYn ? '증권사 사용을 중지했습니다.' : '증권사를 다시 사용합니다.')
    await loadBrokerages()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '사용 상태를 변경하지 못했습니다.'
  }
}

function openAccountCreate() {
  if (!selectedBrokerage.value?.useYn) {
    errorMessage.value = '사용 중인 증권사를 선택해야 계좌를 등록할 수 있습니다.'
    return
  }
  editingAccountId.value = null
  accountForm.value = {
    brokerageId: selectedBrokerage.value.brokerageId,
    accountName: '', accountNumber: '', accountMemo: '', useYn: true,
  }
  showAccountForm.value = true
  errorMessage.value = ''
}

async function openAccountEdit(item: ManagedAccount) {
  try {
    const detail = await investmentMasterApi.getAccount(item.accountId)
    editingAccountId.value = detail.accountId
    accountForm.value = {
      brokerageId: detail.brokerageId, accountName: detail.accountName,
      accountNumber: detail.accountNumber ?? '', accountMemo: detail.accountMemo ?? '', useYn: detail.useYn,
    }
    showAccountForm.value = true
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '계좌 상세정보를 불러오지 못했습니다.'
  }
}

async function saveAccount() {
  if (!accountForm.value.brokerageId || !accountForm.value.accountName.trim()) {
    errorMessage.value = '증권사와 계좌 별칭을 입력해 주세요.'
    return
  }
  saving.value = true
  errorMessage.value = ''
  const payload: AccountPayload = {
    brokerageId: accountForm.value.brokerageId,
    accountName: accountForm.value.accountName.trim(),
    accountNumber: accountForm.value.accountNumber.trim() || null,
    accountMemo: accountForm.value.accountMemo.trim() || null,
    useYn: accountForm.value.useYn,
  }
  try {
    if (editingAccountId.value === null) await investmentMasterApi.createAccount(payload)
    else await investmentMasterApi.updateAccount(editingAccountId.value, payload)
    showAccountForm.value = false
    setNotice(editingAccountId.value === null ? '계좌가 등록되었습니다.' : '계좌가 수정되었습니다.')
    await loadAccounts()
    await loadBrokerages()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '계좌를 저장하지 못했습니다.'
  } finally { saving.value = false }
}

async function toggleAccount(item: ManagedAccount) {
  try {
    await investmentMasterApi.updateAccountUsage(item.accountId, !item.useYn)
    setNotice(item.useYn ? '계좌 사용을 중지했습니다.' : '계좌를 다시 사용합니다.')
    await loadAccounts()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '사용 상태를 변경하지 못했습니다.'
  }
}

async function changeBrokeragePage(next: number) {
  if (next < 0 || next >= brokeragePages.value) return
  brokeragePage.value = next
  await loadBrokerages(false)
}

async function changeAccountPage(next: number) {
  if (next < 0 || next >= accountPages.value) return
  accountPage.value = next
  await loadAccounts()
}

onMounted(() => loadBrokerages(false))
</script>

<template>
  <div class="settings-page">
    <header class="topbar">
      <RouterLink class="brand" to="/"><span class="brand-mark"><i class="pi pi-chart-line"></i></span><span><strong>Asset Note</strong><small>투자 거래 일지</small></span></RouterLink>
      <RouterLink class="back-link" to="/"><i class="pi pi-arrow-left"></i> 거래 내역으로</RouterLink>
    </header>

    <main class="page-shell">
      <section class="page-heading"><div><p>ACCOUNT DIRECTORY</p><h1>증권사·계좌 관리</h1><span>거래 기록에 사용할 증권사와 투자 계좌를 관리합니다.</span></div></section>
      <div v-if="errorMessage && !showBrokerageForm && !showAccountForm" class="alert"><i class="pi pi-exclamation-circle"></i>{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="toast"><i class="pi pi-check-circle"></i>{{ noticeMessage }}</div>

      <div class="master-grid">
        <section class="panel brokerage-panel">
          <header><div><h2>증권사</h2><span>{{ brokerageTotal }}개</span></div><button class="primary icon-button" type="button" title="증권사 등록" @click="openBrokerageCreate"><i class="pi pi-plus"></i></button></header>
          <form class="search-row" @submit.prevent="searchBrokerages"><div class="search-box"><i class="pi pi-search"></i><input v-model="brokerageKeyword" placeholder="증권사명 검색" /></div><select v-model="brokerageUseYn" @change="searchBrokerages"><option value="">전체</option><option value="true">사용 중</option><option value="false">사용 중지</option></select></form>
          <div class="brokerage-list">
            <div v-if="loadingBrokerages" class="empty"><i class="pi pi-spin pi-spinner"></i>불러오는 중</div>
            <div v-else-if="brokerages.length === 0" class="empty"><i class="pi pi-building"></i><strong>등록된 증권사가 없습니다</strong><button type="button" @click="openBrokerageCreate">첫 증권사 등록</button></div>
            <button v-for="item in brokerages" v-else :key="item.brokerageId" class="brokerage-item" :class="{ selected: selectedBrokerageId === item.brokerageId }" type="button" @click="selectBrokerage(item.brokerageId)">
              <span class="company-icon"><i class="pi pi-building-columns"></i></span><span class="company-copy"><strong>{{ item.brokerageName }}</strong><small>등록 계좌 {{ item.accountCount }}개</small></span><span class="status" :class="{ off: !item.useYn }">{{ item.useYn ? '사용 중' : '중지' }}</span><span class="item-actions"><i class="pi pi-pencil" title="수정" @click.stop="openBrokerageEdit(item)"></i><i :class="item.useYn ? 'pi pi-pause-circle' : 'pi pi-play-circle'" title="상태 변경" @click.stop="toggleBrokerage(item)"></i></span>
            </button>
          </div>
          <nav v-if="brokeragePages > 1" class="pager"><button :disabled="brokeragePage === 0" @click="changeBrokeragePage(brokeragePage - 1)"><i class="pi pi-chevron-left"></i></button><span>{{ brokeragePage + 1 }} / {{ brokeragePages }}</span><button :disabled="brokeragePage + 1 >= brokeragePages" @click="changeBrokeragePage(brokeragePage + 1)"><i class="pi pi-chevron-right"></i></button></nav>
        </section>

        <section class="panel account-panel">
          <header><div><p>{{ selectedBrokerage?.brokerageName || '증권사 선택' }}</p><h2>투자 계좌</h2><span>{{ accountTotal }}개의 계좌</span></div><button class="primary" type="button" :disabled="!selectedBrokerage?.useYn" @click="openAccountCreate"><i class="pi pi-plus"></i> 계좌 등록</button></header>
          <form class="search-row account-search" @submit.prevent="searchAccounts"><div class="search-box"><i class="pi pi-search"></i><input v-model="accountKeyword" :disabled="!selectedBrokerageId" placeholder="계좌 별칭 또는 계좌번호 검색" /></div><select v-model="accountUseYn" :disabled="!selectedBrokerageId" @change="searchAccounts"><option value="">전체 상태</option><option value="true">사용 중</option><option value="false">사용 중지</option></select></form>
          <div class="account-table-wrap">
            <table>
              <thead><tr><th>계좌 별칭</th><th>계좌번호</th><th>메모</th><th>상태</th><th></th></tr></thead>
              <tbody>
                <tr v-if="loadingAccounts"><td colspan="5" class="empty"><i class="pi pi-spin pi-spinner"></i>불러오는 중</td></tr>
                <tr v-else-if="!selectedBrokerageId"><td colspan="5" class="empty"><i class="pi pi-arrow-left"></i><strong>증권사를 선택해 주세요</strong></td></tr>
                <tr v-else-if="accounts.length === 0"><td colspan="5" class="empty"><i class="pi pi-wallet"></i><strong>등록된 계좌가 없습니다</strong><span>선택한 증권사의 거래 계좌를 등록하세요.</span></td></tr>
                <tr v-for="item in accounts" v-else :key="item.accountId"><td><strong>{{ item.accountName }}</strong></td><td class="account-number">{{ item.accountNumber || '—' }}</td><td class="memo">{{ item.accountMemo || '—' }}</td><td><span class="status" :class="{ off: !item.useYn }">{{ item.useYn ? '사용 중' : '중지' }}</span></td><td><div class="row-actions"><button title="수정" @click="openAccountEdit(item)"><i class="pi pi-pencil"></i></button><button title="상태 변경" @click="toggleAccount(item)"><i :class="item.useYn ? 'pi pi-pause-circle' : 'pi pi-play-circle'"></i></button></div></td></tr>
              </tbody>
            </table>
          </div>
          <nav v-if="accountPages > 1" class="pager"><button :disabled="accountPage === 0" @click="changeAccountPage(accountPage - 1)"><i class="pi pi-chevron-left"></i></button><span>{{ accountPage + 1 }} / {{ accountPages }}</span><button :disabled="accountPage + 1 >= accountPages" @click="changeAccountPage(accountPage + 1)"><i class="pi pi-chevron-right"></i></button></nav>
        </section>
      </div>
    </main>

    <div v-if="showBrokerageForm" class="modal-backdrop" @click.self="showBrokerageForm = false">
      <section class="modal" role="dialog" aria-modal="true"><header><div><p>COMPANY</p><h2>{{ editingBrokerageId === null ? '증권사 등록' : '증권사 수정' }}</h2></div><button @click="showBrokerageForm = false"><i class="pi pi-times"></i></button></header><form @submit.prevent="saveBrokerage"><label><span>증권사명 <b>*</b></span><input v-model="brokerageForm.brokerageName" maxlength="100" placeholder="예: 미래에셋증권" autofocus required /></label><label class="check"><input v-model="brokerageForm.useYn" type="checkbox" /><span>사용 중인 증권사</span></label><div v-if="errorMessage" class="alert"><i class="pi pi-exclamation-circle"></i>{{ errorMessage }}</div><footer><button type="button" class="secondary" @click="showBrokerageForm = false">취소</button><button class="primary" :disabled="saving"><i :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i> 저장</button></footer></form></section>
    </div>

    <div v-if="showAccountForm" class="modal-backdrop" @click.self="showAccountForm = false">
      <section class="modal account-modal" role="dialog" aria-modal="true"><header><div><p>INVESTMENT ACCOUNT</p><h2>{{ editingAccountId === null ? '계좌 등록' : '계좌 수정' }}</h2></div><button @click="showAccountForm = false"><i class="pi pi-times"></i></button></header><form @submit.prevent="saveAccount"><label><span>증권사 <b>*</b></span><select v-model="accountForm.brokerageId" required><option v-for="item in activeBrokerages" :key="item.brokerageId" :value="item.brokerageId">{{ item.brokerageName }}</option></select></label><label><span>계좌 별칭 <b>*</b></span><input v-model="accountForm.accountName" maxlength="100" placeholder="예: 장기투자 계좌" required /></label><label><span>계좌번호</span><input v-model="accountForm.accountNumber" maxlength="100" placeholder="선택 입력" /></label><label><span>메모</span><textarea v-model="accountForm.accountMemo" rows="4" placeholder="계좌의 투자 목적이나 참고사항"></textarea></label><label class="check"><input v-model="accountForm.useYn" type="checkbox" /><span>사용 중인 계좌</span></label><div v-if="errorMessage" class="alert"><i class="pi pi-exclamation-circle"></i>{{ errorMessage }}</div><footer><button type="button" class="secondary" @click="showAccountForm = false">취소</button><button class="primary" :disabled="saving"><i :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i> 저장</button></footer></form></section>
    </div>
  </div>
</template>

<style scoped>
.settings-page{min-height:100vh;color:#18211d;background:#f3f5f1}button{width:auto}.topbar{height:74px;padding:0 max(24px,calc((100vw - 1320px)/2));display:flex;align-items:center;justify-content:space-between;background:#fafbf8;border-bottom:1px solid #dfe4dc}.brand{display:flex;align-items:center;gap:11px;color:inherit;text-decoration:none}.brand-mark{width:38px;height:38px;display:grid;place-items:center;color:#fff;background:#173f35;border-radius:11px}.brand>span:last-child{display:grid}.brand small{margin-top:4px;color:#7b8580;font-size:11px}.back-link{display:flex;align-items:center;gap:7px;color:#52635b;font-size:13px;font-weight:700;text-decoration:none}.page-shell{width:min(1320px,calc(100% - 48px));margin:auto;padding:48px 0 80px}.page-heading{margin-bottom:28px}.page-heading p,.modal header p{margin:0 0 9px;color:#598077;font-size:11px;font-weight:800;letter-spacing:.15em}.page-heading h1{margin:0;font:500 clamp(34px,4vw,48px) Georgia,serif;letter-spacing:-.04em}.page-heading span{display:block;margin-top:10px;color:#747e79}.master-grid{display:grid;grid-template-columns:380px minmax(0,1fr);gap:18px}.panel{display:flex;flex-direction:column;min-height:620px;background:#fff;border:1px solid #dfe4dc;border-radius:18px;box-shadow:0 8px 30px rgba(42,58,49,.04);overflow:hidden}.panel>header{min-height:82px;padding:20px 22px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e6eae6}.panel>header h2{display:inline;margin:0;font-size:18px}.panel>header span{margin-left:7px;color:#89928d;font-size:12px}.account-panel>header p{margin:0 0 5px;color:#688178;font-size:11px;font-weight:700}.account-panel>header h2{display:block}.primary{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:40px;padding:0 15px;color:#fff;background:#173f35;border-radius:9px;font-weight:700}.primary:hover{background:#0d3028}.primary:disabled{opacity:.45}.icon-button{width:40px;padding:0}.search-row{display:flex;gap:8px;padding:14px;border-bottom:1px solid #e9ece9}.search-box{position:relative;flex:1}.search-box i{position:absolute;left:12px;top:50%;transform:translateY(-50%);color:#96a09a;font-size:12px}.search-box input{padding-left:34px}.search-row select{width:105px}.search-row input,.search-row select,.modal input,.modal select,.modal textarea{height:40px;border:1px solid #d8ded9;border-radius:8px;background:#fcfdfb;color:#202a25;font:inherit;font-size:12px;outline:none}.search-row input{width:100%}.search-row select{padding:0 9px}.brokerage-list{flex:1;padding:8px}.brokerage-item{width:100%;display:grid;grid-template-columns:38px 1fr auto;grid-template-areas:'icon copy status' 'icon actions actions';gap:3px 11px;padding:13px;border:1px solid transparent;border-radius:11px;color:#26312b;background:transparent;text-align:left}.brokerage-item:hover{background:#f6f8f5}.brokerage-item.selected{border-color:#c9d8d2;background:#edf3f0}.company-icon{grid-area:icon;width:38px;height:38px;display:grid;place-items:center;color:#52756c;background:#e7efeb;border-radius:9px}.company-copy{grid-area:copy;display:grid;gap:4px}.company-copy small{color:#8b948f}.status{grid-area:status;align-self:start;padding:4px 7px;color:#32705f;background:#e6f3ed;border-radius:20px;font-size:10px;font-weight:800;white-space:nowrap}.status.off{color:#7b817e;background:#ecefed}.item-actions{grid-area:actions;display:flex;justify-content:end;gap:12px;color:#7c8881;opacity:0}.brokerage-item:hover .item-actions,.brokerage-item.selected .item-actions{opacity:1}.account-table-wrap{flex:1;overflow-x:auto}table{width:100%;min-width:680px;border-collapse:collapse}th{padding:12px 15px;color:#7d8782;background:#f8f9f7;font-size:11px;text-align:left}td{padding:15px;border-top:1px solid #edf0ec;font-size:13px}.account-number{font-variant-numeric:tabular-nums;color:#5d6963}.memo{max-width:230px;color:#737d78;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.row-actions{display:flex;justify-content:end;gap:5px}.row-actions button,.modal header button{width:32px;height:32px;padding:0;color:#65706a;background:#f3f5f2}.empty{padding:80px 20px!important;color:#8a948e;text-align:center!important}.empty i,.empty strong,.empty span{display:block;margin-bottom:9px}.empty i{font-size:26px}.empty button{padding:8px 12px;color:#173f35;background:#e9f0ed}.pager{display:flex;align-items:center;justify-content:center;gap:12px;padding:13px;border-top:1px solid #e6eae6;color:#707b75;font-size:11px}.pager button{width:30px;height:30px;padding:0;color:#435049;background:#f1f4f1}.alert{display:flex;align-items:center;gap:8px;margin-bottom:14px;padding:12px 14px;color:#973f34;background:#fff0ed;border:1px solid #f2d8d2;border-radius:9px;font-size:12px}.toast{position:fixed;right:25px;top:90px;z-index:80;padding:13px 17px;color:#fff;background:#173f35;border-radius:9px}.toast i{margin-right:7px}.modal-backdrop{position:fixed;inset:0;z-index:100;display:grid;place-items:center;padding:20px;background:rgba(17,29,23,.58);backdrop-filter:blur(3px)}.modal{width:min(480px,100%);overflow:hidden;background:#fff;border-radius:18px;box-shadow:0 30px 80px rgba(13,30,22,.25)}.modal header{display:flex;align-items:start;justify-content:space-between;padding:23px 25px 17px;border-bottom:1px solid #e5e9e5}.modal h2{margin:0;font:500 27px Georgia,serif}.modal form{display:grid;gap:17px;padding:22px 25px 25px}.modal label{display:grid;gap:7px;color:#606b65;font-size:12px;font-weight:700}.modal label b{color:#b45545}.modal input,.modal select{width:100%;padding:0 11px}.modal textarea{width:100%;height:auto;padding:11px;resize:vertical}.modal .check{display:flex;align-items:center;gap:8px}.modal .check input{width:17px;height:17px}.modal footer{display:flex;justify-content:end;gap:8px;padding-top:4px}.secondary{min-height:40px;padding:0 16px;color:#57625c;background:#eef1ee}.modal .alert{margin:0}.account-modal{width:min(540px,100%)}
@media(max-width:900px){.master-grid{grid-template-columns:1fr}.panel{min-height:480px}}
@media(max-width:600px){.topbar{height:66px;padding:0 15px}.back-link{font-size:0}.back-link i{font-size:14px}.page-shell{width:calc(100% - 28px);padding-top:32px}.panel>header{padding:17px}.search-row{flex-direction:column}.search-row select{width:100%}.modal-backdrop{padding:0;align-items:end}.modal{border-radius:18px 18px 0 0}.toast{left:14px;right:14px;top:78px}}
</style>
