<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import DataTable from "primevue/datatable";
import type { DataTableSortEvent } from "primevue/datatable";
import Column from "primevue/column";
import {
  investmentMasterApi,
  type ManagedBrokerage,
  type ManagedAccount,
  type BrokeragePayload,
  type AccountPayload,
  type AccountMemo,
} from "@/services/investmentMasterApi";

type AccountFormMode = "create" | "edit" | "view";

const brokerages = ref<ManagedBrokerage[]>([]);
const accounts = ref<ManagedAccount[]>([]);
const selectedBrokerageId = ref<number | null>(null);
const brokerageKeyword = ref("");
const brokerageUseYn = ref("");
const accountKeyword = ref("");
const accountUseYn = ref("");
const brokeragePage = ref(0);
const brokeragePages = ref(0);
const brokerageTotal = ref(0);
const accountPage = ref(0);
const accountTotal = ref(0);
type AccountSortField =
  "accountNumber" | "accountName" | "latestMemoContent" | "useYn";
const accountSortField = ref<AccountSortField>("accountNumber");
const accountSortOrder = ref<1 | -1>(1);
const loadingBrokerages = ref(false);
const loadingAccounts = ref(false);
const loadingMemos = ref(false);
const saving = ref(false);
const errorMessage = ref("");
const noticeMessage = ref("");
const showBrokerageForm = ref(false);
const showAccountForm = ref(false);
const showMemoForm = ref(false);
const accountFormMode = ref<AccountFormMode>("create");
const editingBrokerageId = ref<number | null>(null);
const editingAccountId = ref<number | null>(null);
const brokerageForm = ref({ brokerageName: "", useYn: true });
const accountForm = ref({
  brokerageId: 0,
  accountName: "",
  accountNumber: "",
  useYn: true,
});
const accountMemos = ref<AccountMemo[]>([]);
const memoContent = ref("");
const editingMemoId = ref<number | null>(null);
const pendingMemoContent = ref("");
const memoErrorMessage = ref("");
const memoHistoryErrorMessage = ref("");
const pageSize = 20;

const selectedBrokerage = computed(
  () =>
    brokerages.value.find(
      (item) => item.brokerageId === selectedBrokerageId.value,
    ) ?? null,
);
const activeBrokerages = computed(() =>
  brokerages.value.filter((item) => item.useYn),
);

function params(
  page: number,
  keyword: string,
  useYn: string,
  brokerageId?: number,
) {
  const query = new URLSearchParams({
    page: String(page),
    size: String(pageSize),
  });
  if (keyword.trim()) query.set("keyword", keyword.trim());
  if (useYn !== "") query.set("useYn", useYn);
  if (brokerageId) query.set("brokerageId", String(brokerageId));
  return query;
}

function setNotice(message: string) {
  noticeMessage.value = message;
  window.setTimeout(() => {
    if (noticeMessage.value === message) noticeMessage.value = "";
  }, 3000);
}

async function loadBrokerages(keepSelection = true) {
  loadingBrokerages.value = true;
  errorMessage.value = "";
  try {
    const result = await investmentMasterApi.getBrokerages(
      params(brokeragePage.value, brokerageKeyword.value, brokerageUseYn.value),
    );
    brokerages.value = result.content;
    brokeragePages.value = result.totalPages;
    brokerageTotal.value = result.totalElements;
    const selectedExists = brokerages.value.some(
      (item) => item.brokerageId === selectedBrokerageId.value,
    );
    if (!keepSelection || !selectedExists)
      selectedBrokerageId.value = brokerages.value[0]?.brokerageId ?? null;
    await loadAccounts();
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "증권사 정보를 불러오지 못했습니다.";
  } finally {
    loadingBrokerages.value = false;
  }
}

async function loadAccounts() {
  if (!selectedBrokerageId.value) {
    accounts.value = [];
    accountTotal.value = 0;
    return;
  }
  loadingAccounts.value = true;
  errorMessage.value = "";
  try {
    const query = params(
      accountPage.value,
      accountKeyword.value,
      accountUseYn.value,
      selectedBrokerageId.value,
    );
    query.set("sortField", accountSortField.value);
    query.set("sortDirection", accountSortOrder.value === 1 ? "asc" : "desc");
    const result = await investmentMasterApi.getAccounts(query);
    accounts.value = result.content;
    accountTotal.value = result.totalElements;
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "계좌 정보를 불러오지 못했습니다.";
  } finally {
    loadingAccounts.value = false;
  }
}

async function selectBrokerage(id: number) {
  selectedBrokerageId.value = id;
  accountPage.value = 0;
  accountKeyword.value = "";
  accountUseYn.value = "";
  await loadAccounts();
}

async function searchBrokerages() {
  brokeragePage.value = 0;
  await loadBrokerages(false);
}
async function searchAccounts() {
  accountPage.value = 0;
  await loadAccounts();
}

function openBrokerageCreate() {
  editingBrokerageId.value = null;
  brokerageForm.value = { brokerageName: "", useYn: true };
  showBrokerageForm.value = true;
  errorMessage.value = "";
}

function openBrokerageEdit(item: ManagedBrokerage) {
  editingBrokerageId.value = item.brokerageId;
  brokerageForm.value = {
    brokerageName: item.brokerageName,
    useYn: item.useYn,
  };
  showBrokerageForm.value = true;
  errorMessage.value = "";
}

async function saveBrokerage() {
  const name = brokerageForm.value.brokerageName.trim();
  if (!name) {
    errorMessage.value = "증권사명을 입력해 주세요.";
    return;
  }
  saving.value = true;
  errorMessage.value = "";
  const payload: BrokeragePayload = {
    brokerageName: name,
    useYn: brokerageForm.value.useYn,
  };
  try {
    const saved =
      editingBrokerageId.value === null
        ? await investmentMasterApi.createBrokerage(payload)
        : await investmentMasterApi.updateBrokerage(
            editingBrokerageId.value,
            payload,
          );
    selectedBrokerageId.value = saved.brokerageId;
    showBrokerageForm.value = false;
    setNotice(
      editingBrokerageId.value === null
        ? "증권사가 등록되었습니다."
        : "증권사가 수정되었습니다.",
    );
    await loadBrokerages();
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "증권사를 저장하지 못했습니다.";
  } finally {
    saving.value = false;
  }
}

async function toggleBrokerage(item: ManagedBrokerage) {
  if (
    item.useYn &&
    !window.confirm(
      "증권사를 사용 중지하면 거래 등록 화면에서 하위 계좌가 숨겨집니다. 계속할까요?",
    )
  )
    return;
  try {
    await investmentMasterApi.updateBrokerageUsage(
      item.brokerageId,
      !item.useYn,
    );
    setNotice(
      item.useYn ? "증권사 사용을 중지했습니다." : "증권사를 다시 사용합니다.",
    );
    await loadBrokerages();
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "사용 상태를 변경하지 못했습니다.";
  }
}

function openAccountCreate() {
  if (!selectedBrokerage.value?.useYn) {
    errorMessage.value =
      "사용 중인 증권사를 선택해야 계좌를 등록할 수 있습니다.";
    return;
  }
  editingAccountId.value = null;
  accountFormMode.value = "create";
  resetAccountMemos();
  accountForm.value = {
    brokerageId: selectedBrokerage.value.brokerageId,
    accountName: "",
    accountNumber: "",
    useYn: true,
  };
  showAccountForm.value = true;
  errorMessage.value = "";
}

async function openAccount(item: ManagedAccount, mode: AccountFormMode) {
  try {
    const detail = await investmentMasterApi.getAccount(item.accountId);
    resetAccountMemos();
    editingAccountId.value = detail.accountId;
    accountFormMode.value = mode;
    accountForm.value = {
      brokerageId: detail.brokerageId,
      accountName: detail.accountName,
      accountNumber: detail.accountNumber ?? "",
      useYn: detail.useYn,
    };
    showAccountForm.value = true;
    errorMessage.value = "";
    await loadAccountMemos(detail.accountId);
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "계좌 상세정보를 불러오지 못했습니다.";
  }
}

async function openAccountEdit(item: ManagedAccount) {
  await openAccount(item, "edit");
}

async function openAccountView(item: ManagedAccount) {
  await openAccount(item, "view");
}

function closeAccountForm() {
  showAccountForm.value = false;
  closeMemoForm();
  editingAccountId.value = null;
  accountFormMode.value = "create";
  resetAccountMemos();
}

function openMemoForm() {
  editingMemoId.value = null;
  memoContent.value = pendingMemoContent.value;
  memoErrorMessage.value = "";
  showMemoForm.value = true;
}

function openMemoEdit(memo: AccountMemo) {
  editingMemoId.value = memo.memoId;
  memoContent.value = memo.memoContent;
  memoErrorMessage.value = "";
  showMemoForm.value = true;
}

function closeMemoForm() {
  showMemoForm.value = false;
  memoContent.value = "";
  editingMemoId.value = null;
  memoErrorMessage.value = "";
}

function resetAccountMemos() {
  accountMemos.value = [];
  memoContent.value = "";
  editingMemoId.value = null;
  pendingMemoContent.value = "";
  memoErrorMessage.value = "";
  memoHistoryErrorMessage.value = "";
  loadingMemos.value = false;
}

async function saveAccount() {
  if (!accountForm.value.brokerageId || !accountForm.value.accountName.trim()) {
    errorMessage.value = "증권사와 계좌 별칭을 입력해 주세요.";
    return;
  }
  saving.value = true;
  errorMessage.value = "";
  const accountId = editingAccountId.value;
  const isNewAccount = accountId === null;
  const payload: AccountPayload = {
    brokerageId: accountForm.value.brokerageId,
    accountName: accountForm.value.accountName.trim(),
    accountNumber: accountForm.value.accountNumber.trim() || null,
    useYn: accountForm.value.useYn,
  };
  try {
    const savedAccount = isNewAccount
      ? await investmentMasterApi.createAccount(payload)
      : await investmentMasterApi.updateAccount(accountId, payload);
    let memoSaveError = "";
    if (pendingMemoContent.value.trim()) {
      try {
        await investmentMasterApi.createAccountMemo(savedAccount.accountId, {
          memoContent: pendingMemoContent.value.trim(),
        });
      } catch (error) {
        memoSaveError =
          error instanceof Error
            ? error.message
            : "메모를 등록하지 못했습니다.";
      }
    }
    editingAccountId.value = savedAccount.accountId;
    accountFormMode.value = "view";
    pendingMemoContent.value = "";
    memoContent.value = "";
    memoErrorMessage.value = "";
    await loadAccountMemos(savedAccount.accountId);
    if (memoSaveError) {
      errorMessage.value = `계좌는 저장되었지만 메모를 등록하지 못했습니다. ${memoSaveError}`;
    } else {
      setNotice(
        isNewAccount ? "계좌가 등록되었습니다." : "계좌가 수정되었습니다.",
      );
    }
    await loadAccounts();
    await loadBrokerages();
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "계좌를 저장하지 못했습니다.";
  } finally {
    saving.value = false;
  }
}

async function toggleAccount(item: ManagedAccount) {
  try {
    await investmentMasterApi.updateAccountUsage(item.accountId, !item.useYn);
    setNotice(
      item.useYn ? "계좌 사용을 중지했습니다." : "계좌를 다시 사용합니다.",
    );
    await loadAccounts();
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : "사용 상태를 변경하지 못했습니다.";
  }
}

async function deleteAccount() {
  const accountId = editingAccountId.value;
  if (accountId === null) return;
  if (
    !window.confirm(
      `'${accountForm.value.accountName}' 계좌와 등록된 메모·거래 내역을 모두 삭제할까요?`,
    )
  )
    return;
  try {
    await investmentMasterApi.deleteAccount(accountId);
    closeAccountForm();
    setNotice("계좌가 삭제되었습니다.");
    if (accounts.value.length === 1 && accountPage.value > 0)
      accountPage.value -= 1;
    await loadAccounts();
    await loadBrokerages();
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "계좌를 삭제하지 못했습니다.";
  }
}

async function loadAccountMemos(accountId = editingAccountId.value) {
  if (accountId === null) return;
  loadingMemos.value = true;
  memoHistoryErrorMessage.value = "";
  try {
    const memos = await investmentMasterApi.getAccountMemos(accountId);
    if (editingAccountId.value === accountId) accountMemos.value = memos;
  } catch (error) {
    if (editingAccountId.value === accountId) {
      memoHistoryErrorMessage.value =
        error instanceof Error
          ? error.message
          : "메모 이력을 불러오지 못했습니다.";
    }
  } finally {
    loadingMemos.value = false;
  }
}

async function deleteAccountMemo(memo: AccountMemo) {
  const accountId = editingAccountId.value;
  if (accountId === null) return;
  if (!window.confirm("이 메모를 삭제할까요?")) return;
  try {
    await investmentMasterApi.deleteAccountMemo(accountId, memo.memoId);
    setNotice("메모가 삭제되었습니다.");
    await Promise.all([loadAccountMemos(accountId), loadAccounts()]);
  } catch (error) {
    memoHistoryErrorMessage.value =
      error instanceof Error ? error.message : "메모를 삭제하지 못했습니다.";
  }
}

async function saveAccountMemo() {
  const content = memoContent.value.trim();
  if (!content) {
    memoErrorMessage.value = "메모를 입력해 주세요.";
    return;
  }
  const accountId = editingAccountId.value;
  if (editingMemoId.value !== null && accountId !== null) {
    try {
      await investmentMasterApi.updateAccountMemo(
        accountId,
        editingMemoId.value,
        {
          memoContent: content,
        },
      );
      closeMemoForm();
      setNotice("메모가 수정되었습니다.");
      await Promise.all([loadAccountMemos(accountId), loadAccounts()]);
    } catch (error) {
      memoErrorMessage.value =
        error instanceof Error ? error.message : "메모를 수정하지 못했습니다.";
    }
    return;
  }
  pendingMemoContent.value = content;
  closeMemoForm();
}

function formatMemoDate(value: string) {
  return new Date(value).toLocaleString("ko-KR");
}

async function changeBrokeragePage(next: number) {
  if (next < 0 || next >= brokeragePages.value) return;
  brokeragePage.value = next;
  await loadBrokerages(false);
}

async function onAccountPage(event: { page: number }) {
  if (event.page === accountPage.value) return;
  accountPage.value = event.page;
  await loadAccounts();
}

async function onAccountSort(event: DataTableSortEvent) {
  const fields: AccountSortField[] = [
    "accountNumber",
    "accountName",
    "latestMemoContent",
    "useYn",
  ];
  if (
    typeof event.sortField !== "string" ||
    !fields.includes(event.sortField as AccountSortField)
  )
    return;
  accountSortField.value = event.sortField as AccountSortField;
  accountSortOrder.value = event.sortOrder === -1 ? -1 : 1;
  accountPage.value = 0;
  await loadAccounts();
}

onMounted(() => loadBrokerages(false));
</script>

<template>
  <div class="settings-page">
    <main class="page-shell">
      <section class="page-heading">
        <div>
          <p>ACCOUNT DIRECTORY</p>
          <h1>증권사·계좌 관리</h1>
          <span>거래 기록에 사용할 증권사와 투자 계좌를 관리합니다.</span>
        </div>
      </section>
      <div
        v-if="errorMessage && !showBrokerageForm && !showAccountForm"
        class="alert"
      >
        <i class="pi pi-exclamation-circle"></i>{{ errorMessage }}
      </div>
      <div v-if="noticeMessage" class="toast">
        <i class="pi pi-check-circle"></i>{{ noticeMessage }}
      </div>

      <div class="master-grid">
        <section class="panel brokerage-panel">
          <header>
            <div>
              <h2>증권사</h2>
              <span>{{ brokerageTotal }}개</span>
            </div>
            <button
              class="primary icon-button"
              type="button"
              title="증권사 등록"
              @click="openBrokerageCreate"
            >
              <i class="pi pi-plus"></i>
            </button>
          </header>
          <form class="search-row" @submit.prevent="searchBrokerages">
            <div class="search-box">
              <i class="pi pi-search"></i
              ><input v-model="brokerageKeyword" placeholder="증권사명 검색" />
            </div>
            <select v-model="brokerageUseYn" @change="searchBrokerages">
              <option value="">전체</option>
              <option value="true">사용 중</option>
              <option value="false">사용 중지</option>
            </select>
          </form>
          <div class="brokerage-list">
            <div v-if="loadingBrokerages" class="empty">
              <i class="pi pi-spin pi-spinner"></i>불러오는 중
            </div>
            <div v-else-if="brokerages.length === 0" class="empty">
              <i class="pi pi-building"></i
              ><strong>등록된 증권사가 없습니다</strong
              ><button type="button" @click="openBrokerageCreate">
                첫 증권사 등록
              </button>
            </div>
            <button
              v-for="item in brokerages"
              v-else
              :key="item.brokerageId"
              class="brokerage-item"
              :class="{ selected: selectedBrokerageId === item.brokerageId }"
              type="button"
              @click="selectBrokerage(item.brokerageId)"
            >
              <span class="company-icon"
                ><i class="pi pi-building-columns"></i></span
              ><span class="company-copy"
                ><strong>{{ item.brokerageName }}</strong
                ><small>등록 계좌 {{ item.accountCount }}개</small></span
              ><span class="status" :class="{ off: !item.useYn }">{{
                item.useYn ? "사용 중" : "중지"
              }}</span
              ><span class="item-actions"
                ><i
                  class="pi pi-pencil"
                  title="수정"
                  @click.stop="openBrokerageEdit(item)"
                ></i
                ><i
                  :class="
                    item.useYn ? 'pi pi-pause-circle' : 'pi pi-play-circle'
                  "
                  title="상태 변경"
                  @click.stop="toggleBrokerage(item)"
                ></i
              ></span>
            </button>
          </div>
          <nav v-if="brokeragePages > 1" class="pager">
            <button
              :disabled="brokeragePage === 0"
              @click="changeBrokeragePage(brokeragePage - 1)"
            >
              <i class="pi pi-chevron-left"></i></button
            ><span>{{ brokeragePage + 1 }} / {{ brokeragePages }}</span
            ><button
              :disabled="brokeragePage + 1 >= brokeragePages"
              @click="changeBrokeragePage(brokeragePage + 1)"
            >
              <i class="pi pi-chevron-right"></i>
            </button>
          </nav>
        </section>

        <section class="panel account-panel">
          <header>
            <div>
              <p>{{ selectedBrokerage?.brokerageName || "증권사 선택" }}</p>
              <h2>투자 계좌</h2>
            </div>
            <button
              class="primary"
              type="button"
              :disabled="!selectedBrokerage?.useYn"
              @click="openAccountCreate"
            >
              <i class="pi pi-plus"></i> 계좌 등록
            </button>
          </header>
          <form
            class="search-row account-search"
            @submit.prevent="searchAccounts"
          >
            <div class="search-box">
              <i class="pi pi-search"></i
              ><input
                v-model="accountKeyword"
                :disabled="!selectedBrokerageId"
                placeholder="계좌 별칭 또는 계좌번호 검색"
              />
            </div>
            <select
              v-model="accountUseYn"
              :disabled="!selectedBrokerageId"
              @change="searchAccounts"
            >
              <option value="">전체 상태</option>
              <option value="true">사용 중</option>
              <option value="false">사용 중지</option>
            </select>
          </form>
          <div class="account-table-wrap">
            <DataTable
              :value="accounts"
              dataKey="accountId"
              :loading="loadingAccounts"
              paginator
              lazy
              :rows="pageSize"
              :first="accountPage * pageSize"
              :totalRecords="accountTotal"
              :sortField="accountSortField"
              :sortOrder="accountSortOrder"
              scrollable
              :tableStyle="{ minWidth: '860px' }"
              @page="onAccountPage"
              @sort="onAccountSort"
            >
              <Column
                field="accountNumber"
                header="계좌번호"
                sortable
                :style="{ width: '14rem', minWidth: '14rem' }"
                ><template #body="{ data }"
                  ><button
                    type="button"
                    class="account-number"
                    title="계좌 상세 조회"
                    @click="openAccountView(data)"
                  >
                    <i class="pi pi-search"></i>{{ data.accountNumber || "—" }}
                  </button></template
                ></Column
              >
              <Column
                field="accountName"
                header="계좌 별칭"
                sortable
                :style="{ width: '10rem', minWidth: '10rem' }"
                ><template #body="{ data }"
                  ><strong class="account-name">{{
                    data.accountName
                  }}</strong></template
                ></Column
              >
              <Column field="latestMemoContent" header="메모" sortable
                ><template #body="{ data }"
                  ><span class="memo">{{
                    data.latestMemoContent || "—"
                  }}</span></template
                ></Column
              >
              <Column field="useYn" header="상태" sortable
                ><template #body="{ data }"
                  ><span class="status" :class="{ off: !data.useYn }">{{
                    data.useYn ? "사용 중" : "중지"
                  }}</span></template
                ></Column
              >
              <Column header="관리"
                ><template #body="{ data }"
                  ><div class="row-actions">
                    <button
                      type="button"
                      title="수정"
                      @click="openAccountEdit(data)"
                    >
                      <i class="pi pi-pencil"></i></button
                    ><button
                      type="button"
                      title="상태 변경"
                      @click="toggleAccount(data)"
                    >
                      <i
                        :class="
                          data.useYn
                            ? 'pi pi-pause-circle'
                            : 'pi pi-play-circle'
                        "
                      ></i>
                    </button></div></template
              ></Column>
              <template #empty
                ><div class="empty">
                  <template v-if="!selectedBrokerageId"
                    ><i class="pi pi-arrow-left"></i
                    ><strong>증권사를 선택해 주세요</strong></template
                  ><template v-else
                    ><i class="pi pi-wallet"></i
                    ><strong>등록된 계좌가 없습니다</strong
                    ><span
                      >선택한 증권사의 거래 계좌를 등록하세요.</span
                    ></template
                  >
                </div></template
              >
            </DataTable>
          </div>
        </section>
      </div>
    </main>

    <div
      v-if="showBrokerageForm"
      class="modal-backdrop"
      @click.self="showBrokerageForm = false"
    >
      <section class="modal" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>COMPANY</p>
            <h2>
              {{ editingBrokerageId === null ? "증권사 등록" : "증권사 수정" }}
            </h2>
          </div>
          <button @click="showBrokerageForm = false">
            <i class="pi pi-times"></i>
          </button>
        </header>
        <form @submit.prevent="saveBrokerage">
          <label
            ><span class="required-label"><b>*</b> 증권사명</span
            ><input
              v-model="brokerageForm.brokerageName"
              maxlength="100"
              placeholder="예: 미래에셋증권"
              autofocus
              required /></label
          ><label class="check"
            ><input v-model="brokerageForm.useYn" type="checkbox" /><span
              >사용 중인 증권사</span
            ></label
          >
          <div v-if="errorMessage" class="alert">
            <i class="pi pi-exclamation-circle"></i>{{ errorMessage }}
          </div>
          <footer>
            <button
              type="button"
              class="secondary"
              @click="showBrokerageForm = false"
            >
              취소</button
            ><button class="primary" :disabled="saving">
              <i :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"></i>
              저장
            </button>
          </footer>
        </form>
      </section>
    </div>

    <div
      v-if="showAccountForm"
      class="modal-backdrop"
      @click.self="closeAccountForm"
    >
      <section class="modal account-modal" role="dialog" aria-modal="true">
        <header>
          <div>
            <p>INVESTMENT ACCOUNT</p>
            <h2>
              {{
                accountFormMode === "create"
                  ? "계좌 등록"
                  : accountFormMode === "edit"
                    ? "계좌 수정"
                    : "계좌 상세"
              }}
            </h2>
          </div>
          <button @click="closeAccountForm"><i class="pi pi-times"></i></button>
        </header>
        <form @submit.prevent="saveAccount">
          <label
            ><span class="required-label"><b>*</b> 증권사</span
            ><select
              v-model="accountForm.brokerageId"
              :disabled="accountFormMode === 'view'"
              required
            >
              <option
                v-for="item in activeBrokerages"
                :key="item.brokerageId"
                :value="item.brokerageId"
              >
                {{ item.brokerageName }}
              </option>
            </select></label
          ><label
            ><span class="required-label"><b>*</b> 계좌 별칭</span
            ><input
              v-model="accountForm.accountName"
              :disabled="accountFormMode === 'view'"
              maxlength="100"
              placeholder="예: 장기투자 계좌"
              required /></label
          ><label
            ><span>계좌번호</span
            ><input
              v-model="accountForm.accountNumber"
              :disabled="accountFormMode === 'view'"
              maxlength="100"
              placeholder="선택 입력" /></label
          ><label class="check"
            ><input
              v-model="accountForm.useYn"
              :disabled="accountFormMode === 'view'"
              type="checkbox"
            /><span>사용 중인 계좌</span></label
          >
          <div v-if="errorMessage" class="alert">
            <i class="pi pi-exclamation-circle"></i>{{ errorMessage }}
          </div>
          <div v-if="pendingMemoContent" class="memo-preview">
            <span>새 메모</span>
            <p>{{ pendingMemoContent }}</p>
          </div>
          <footer>
            <template v-if="accountFormMode === 'view'">
              <button type="button" class="danger" @click="deleteAccount">
                <i class="pi pi-trash"></i> 삭제
              </button>
              <button type="button" class="secondary" @click="closeAccountForm">
                닫기
              </button>
              <button
                type="button"
                class="primary"
                @click="accountFormMode = 'edit'"
              >
                <i class="pi pi-pencil"></i> 수정
              </button>
            </template>
            <template v-else>
              <button type="button" class="secondary" @click="openMemoForm">
                <i
                  :class="pendingMemoContent ? 'pi pi-pencil' : 'pi pi-plus'"
                ></i>
                {{ pendingMemoContent ? "새 메모 수정" : "새 메모 추가" }}
              </button>
              <button type="button" class="secondary" @click="closeAccountForm">
                취소
              </button>
              <button class="primary" :disabled="saving">
                <i
                  :class="saving ? 'pi pi-spin pi-spinner' : 'pi pi-check'"
                ></i>
                저장
              </button>
            </template>
          </footer>
        </form>
        <section class="memo-section">
          <div class="memo-history">
            <div v-if="editingAccountId === null" class="memo-empty">
              계좌 저장 후 메모 이력이 표시됩니다.
            </div>
            <div v-else-if="loadingMemos" class="memo-empty">
              <i class="pi pi-spin pi-spinner"></i> 불러오는 중
            </div>
            <div v-else-if="memoHistoryErrorMessage" class="memo-empty">
              {{ memoHistoryErrorMessage }}
            </div>
            <div v-else-if="accountMemos.length === 0" class="memo-empty">
              등록된 메모가 없습니다.
            </div>
            <article v-for="memo in accountMemos" v-else :key="memo.memoId">
              <div class="memo-item-meta">
                <time>{{ formatMemoDate(memo.createdAt) }}</time>
                <div class="memo-actions">
                  <button
                    type="button"
                    class="memo-edit"
                    title="메모 수정"
                    @click="openMemoEdit(memo)"
                  >
                    <i class="pi pi-pencil"></i>
                  </button>
                  <button
                    type="button"
                    class="memo-delete"
                    title="메모 삭제"
                    @click="deleteAccountMemo(memo)"
                  >
                    <i class="pi pi-trash"></i>
                  </button>
                </div>
              </div>
              <p>{{ memo.memoContent }}</p>
            </article>
          </div>
        </section>
      </section>
    </div>

    <div
      v-if="showMemoForm"
      class="modal-backdrop memo-modal-backdrop"
      @click.self="closeMemoForm"
    >
      <section
        class="modal memo-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="memo-form-title"
      >
        <header>
          <div>
            <p>ACCOUNT MEMO</p>
            <h2 id="memo-form-title">
              {{ editingMemoId === null ? "새 메모 추가" : "메모 수정" }}
            </h2>
          </div>
          <button
            type="button"
            aria-label="메모 작성 닫기"
            @click="closeMemoForm"
          >
            <i class="pi pi-times"></i>
          </button>
        </header>
        <form @submit.prevent="saveAccountMemo">
          <label
            ><span class="required-label"><b>*</b> 메모</span
            ><textarea
              v-model="memoContent"
              rows="6"
              maxlength="1000"
              placeholder="계좌의 투자 목적이나 참고사항을 기록하세요."
              autofocus
            ></textarea>
          </label>
          <div v-if="memoErrorMessage" class="alert">
            <i class="pi pi-exclamation-circle"></i>{{ memoErrorMessage }}
          </div>
          <footer>
            <button type="button" class="secondary" @click="closeMemoForm">
              취소</button
            ><button class="primary">
              <i class="pi pi-check"></i>
              {{ editingMemoId === null ? "등록" : "저장" }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.settings-page {
  min-height: 100vh;
  color: #18211d;
  background: #f3f5f1;
}
button {
  width: auto;
}
.topbar {
  height: 74px;
  padding: 0 max(24px, calc((100vw - 1320px) / 2));
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fafbf8;
  border-bottom: 1px solid #dfe4dc;
}
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: inherit;
  text-decoration: none;
}
.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #173f35;
  border-radius: 0.2rem;
}
.brand > span:last-child {
  display: grid;
}
.brand small {
  margin-top: 4px;
  color: #7b8580;
  font-size: 11px;
}
.back-link {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #52635b;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}
.page-shell {
  width: min(1320px, calc(100% - 48px));
  margin: auto;
  padding: 48px 0 80px;
}
.page-heading {
  margin-bottom: 28px;
}
.page-heading p,
.modal header p {
  margin: 0 0 9px;
  color: #598077;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.15em;
}
.page-heading h1 {
  margin: 0;
  font:
    500 clamp(34px, 4vw, 48px) Georgia,
    serif;
  letter-spacing: -0.04em;
}
.page-heading span {
  display: block;
  margin-top: 10px;
  color: #747e79;
}
.master-grid {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 18px;
}
.panel {
  display: flex;
  flex-direction: column;
  min-height: 620px;
  background: #fff;
  border: 1px solid #dfe4dc;
  border-radius: 0.2rem;
  box-shadow: 0 8px 30px rgba(42, 58, 49, 0.04);
  overflow: hidden;
}
.panel > header {
  min-height: 82px;
  padding: 20px 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6eae6;
}
.panel > header h2 {
  display: inline;
  margin: 0;
  font-size: 18px;
}
.panel > header span {
  margin-left: 7px;
  color: #89928d;
  font-size: 12px;
}
.account-panel > header p {
  margin: 0 0 5px;
  color: #688178;
  font-size: 11px;
  font-weight: 700;
}
.account-panel > header h2 {
  display: block;
}
.primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 15px;
  color: #fff;
  background: #173f35;
  border-radius: 0.2rem;
  font-weight: 700;
}
.primary:hover {
  background: #0d3028;
}
.primary:disabled {
  opacity: 0.45;
}
.icon-button {
  width: 40px;
  padding: 0;
}
.search-row {
  display: flex;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid #e9ece9;
}
.search-box {
  position: relative;
  flex: 1;
}
.search-box i {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #96a09a;
  font-size: 12px;
}
.search-box input {
  padding-left: 34px;
}
.search-row select {
  width: 105px;
}
.search-row input,
.search-row select,
.modal input,
.modal select,
.modal textarea {
  height: 40px;
  border: 1px solid #d8ded9;
  border-radius: 0.2rem;
  background: #fcfdfb;
  color: #202a25;
  font: inherit;
  font-size: 12px;
  outline: none;
}
.search-row input {
  width: 100%;
}
.search-row select {
  padding: 0 9px;
}
.brokerage-list {
  flex: 1;
  padding: 8px;
}
.brokerage-item {
  width: 100%;
  display: grid;
  grid-template-columns: 38px 1fr auto;
  grid-template-areas: "icon copy status" "icon actions actions";
  gap: 3px 11px;
  padding: 13px;
  border: 1px solid transparent;
  border-radius: 0.2rem;
  color: #26312b;
  background: transparent;
  text-align: left;
}
.brokerage-item:hover {
  background: #f6f8f5;
}
.brokerage-item.selected {
  border-color: #c9d8d2;
  background: #edf3f0;
}
.company-icon {
  grid-area: icon;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  color: #52756c;
  background: #e7efeb;
  border-radius: 0.2rem;
}
.company-copy {
  grid-area: copy;
  display: grid;
  gap: 4px;
}
.company-copy small {
  color: #8b948f;
}
.status {
  grid-area: status;
  align-self: start;
  padding: 4px 7px;
  color: #32705f;
  background: #e6f3ed;
  border-radius: 0.2rem;
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}
.status.off {
  color: #7b817e;
  background: #ecefed;
}
.item-actions {
  grid-area: actions;
  display: flex;
  justify-content: end;
  gap: 12px;
  color: #7c8881;
  opacity: 0;
}
.brokerage-item:hover .item-actions,
.brokerage-item.selected .item-actions {
  opacity: 1;
}
.account-table-wrap {
  flex: 1;
  overflow-x: auto;
}
.account-table-wrap :deep(.p-datatable-thead > tr > th) {
  text-align: left !important;
}
.account-table-wrap :deep(.p-datatable-column-header-content) {
  justify-content: flex-start !important;
}
table {
  width: 100%;
  min-width: 680px;
  border-collapse: collapse;
}
th {
  padding: 12px 15px;
  color: #7d8782;
  background: #f8f9f7;
  font-size: 11px;
  text-align: left;
}
td {
  padding: 15px;
  border-top: 1px solid #edf0ec;
  font-size: 13px;
}
.account-number {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  font-variant-numeric: tabular-nums;
  color: #285d50;
  background: transparent;
  cursor: pointer;
}
.account-number:hover {
  color: #173f35;
}
.account-name {
  display: block;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.memo {
  max-width: 230px;
  color: #737d78;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-actions {
  display: flex;
  justify-content: end;
  gap: 5px;
}
.row-actions button,
.modal header button {
  width: 32px;
  height: 32px;
  padding: 0;
  color: #65706a;
  background: #f3f5f2;
}
.empty {
  padding: 80px 20px !important;
  color: #8a948e;
  text-align: center !important;
}
.empty i,
.empty strong,
.empty span {
  display: block;
  margin-bottom: 9px;
}
.empty i {
  font-size: 26px;
}
.empty button {
  padding: 8px 12px;
  color: #173f35;
  background: #e9f0ed;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 13px;
  border-top: 1px solid #e6eae6;
  color: #707b75;
  font-size: 11px;
}
.pager button {
  width: 30px;
  height: 30px;
  padding: 0;
  color: #435049;
  background: #f1f4f1;
}
.alert {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px 14px;
  color: #973f34;
  background: #fff0ed;
  border: 1px solid #f2d8d2;
  border-radius: 0.2rem;
  font-size: 12px;
}
.toast {
  position: fixed;
  right: 25px;
  top: 90px;
  z-index: 80;
  padding: 13px 17px;
  color: #fff;
  background: #173f35;
  border-radius: 0.2rem;
}
.toast i {
  margin-right: 7px;
}
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(17, 29, 23, 0.58);
  backdrop-filter: blur(3px);
}
.modal {
  width: min(480px, 100%);
  overflow: hidden;
  background: #fff;
  border-radius: 0.2rem;
  box-shadow: 0 30px 80px rgba(13, 30, 22, 0.25);
}
.modal header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  padding: 23px 25px 17px;
  border-bottom: 1px solid #e5e9e5;
}
.modal h2 {
  margin: 0;
  font:
    500 27px Georgia,
    serif;
}
.modal form {
  display: grid;
  gap: 17px;
  padding: 22px 25px 25px;
}
.modal label {
  display: grid;
  gap: 7px;
  color: #606b65;
  font-size: 12px;
  font-weight: 700;
}
.modal label b {
  color: #b45545;
}
.modal input,
.modal select {
  width: 100%;
  padding: 0 11px;
}
.modal textarea {
  width: 100%;
  height: auto;
  padding: 11px;
  resize: vertical;
}
.modal .check {
  display: flex;
  align-items: center;
  gap: 8px;
}
.modal .check input {
  width: 17px;
  height: 17px;
}
.modal footer {
  display: flex;
  justify-content: end;
  gap: 8px;
  padding-top: 4px;
}
.secondary {
  min-height: 40px;
  padding: 0 16px;
  color: #57625c;
  background: #eef1ee;
}
.danger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 16px;
  color: #a34336;
  background: #fff0ed;
  border: 1px solid #f1d7d1;
  border-radius: 0.2rem;
  font-weight: 700;
}
.modal .alert {
  margin: 0;
}
.account-modal {
  width: min(540px, 100%);
}
.settings-page {
  min-height: calc(100vh - 64px);
}
.account-modal {
  width: min(1080px, 100%);
  max-height: calc(100vh - 40px);
  overflow: auto;
}
.account-modal > form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.account-modal > form > .alert,
.account-modal > form > footer {
  grid-column: 1/-1;
}
.memo-section {
  padding: 22px 25px 25px;
  border-top: 1px solid #e5e9e5;
}
.memo-history {
  border-top: 1px solid #e5e9e5;
}
.memo-history article {
  padding: 14px 0;
  border-top: 1px solid #edf0ec;
}
.memo-history article:first-child {
  border-top: 0;
}
.memo-history article p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.55;
}
.memo-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.memo-history time {
  color: #8a948e;
  font-size: 11px;
}
.memo-actions {
  display: flex;
  gap: 5px;
}
.memo-edit,
.memo-delete {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 0.2rem;
}
.memo-edit {
  color: #52756c;
  background: #f1f6f3;
  border: 1px solid #d9e6df;
}
.memo-delete {
  color: #a34336;
  background: #fff5f2;
  border: 1px solid #f1d7d1;
}
.memo-empty {
  padding: 22px 0;
  color: #8a948e;
  font-size: 12px;
  text-align: center;
}
.memo-preview {
  grid-column: 1/-1;
  padding: 14px 16px;
  border: 1px solid #d9e6df;
  border-radius: 0.2rem;
  background: #f3f8f5;
}
.memo-preview span {
  display: block;
  margin-bottom: 6px;
  color: #52756c;
  font-size: 11px;
  font-weight: 800;
}
.memo-preview p {
  margin: 0;
  color: #33423b;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}
.memo-modal-backdrop {
  z-index: 110;
}
.memo-modal {
  width: min(540px, 100%);
}
@media (max-width: 900px) {
  .master-grid {
    grid-template-columns: 1fr;
  }
  .panel {
    min-height: 480px;
  }
}
@media (max-width: 600px) {
  .page-shell {
    width: calc(100% - 28px);
    padding-top: 32px;
  }
  .panel > header {
    padding: 17px;
  }
  .search-row {
    flex-direction: column;
  }
  .search-row select {
    width: 100%;
  }
  .modal-backdrop {
    padding: 0;
    align-items: end;
  }
  .modal {
    border-radius: 0.2rem 0.2rem 0 0;
  }
  .account-modal > form {
    grid-template-columns: 1fr;
  }
  .toast {
    left: 14px;
    right: 14px;
    top: 78px;
  }
}
</style>
