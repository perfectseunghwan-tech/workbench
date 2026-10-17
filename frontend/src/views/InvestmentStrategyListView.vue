<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import {
  investmentStrategyApi,
  statusLabel,
  strategyCategories,
  type StrategySummary,
} from "../services/investmentStrategyApi";
const router = useRouter(),
  rows = ref<StrategySummary[]>([]),
  loading = ref(false),
  error = ref(""),
  keyword = ref(""),
  scope = ref(""),
  category = ref(""),
  status = ref(""),
  sort = ref("updated"),
  page = ref(0),
  total = ref(0);
const categoryName = (code: string) =>
  strategyCategories.find((x) => x[0] === code)?.[1] || code;
const date = (v: string) => new Date(v).toLocaleDateString("ko-KR");
async function load() {
  loading.value = true;
  error.value = "";
  try {
    const p = new URLSearchParams({
      page: String(page.value),
      size: "20",
      sort: sort.value,
    });
    if (keyword.value.trim()) p.set("keyword", keyword.value.trim());
    if (scope.value) p.set("strategyScope", scope.value);
    if (category.value) p.set("categoryCode", category.value);
    if (status.value) p.set("status", status.value);
    const r = await investmentStrategyApi.list(p);
    rows.value = r.content;
    total.value = r.totalElements;
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "목록을 불러오지 못했습니다.";
  } finally {
    loading.value = false;
  }
}
function search() {
  page.value = 0;
  load();
}
function reset() {
  keyword.value = "";
  scope.value = "";
  category.value = "";
  status.value = "";
  sort.value = "updated";
  search();
}
async function remove(row: StrategySummary) {
  if (!confirm(`'${row.title}' 전략을 삭제하시겠습니까?`)) return;
  try {
    await investmentStrategyApi.remove(row.strategyId);
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "삭제하지 못했습니다.";
  }
}
onMounted(load);
</script>
<template>
  <div class="strategy-page">
    <header class="strategy-head">
      <div>
        <p>INVESTMENT STRATEGY</p>
        <h1>투자전략 관리</h1>
        <span>투자 원칙과 분석, 실행 기록을 한곳에서 관리합니다.</span>
      </div>
      <button class="primary" @click="router.push('/strategies/new')">
        <i class="pi pi-plus" /> 전략 등록
      </button>
    </header>
    <section class="strategy-card filters">
      <input
        v-model="keyword"
        placeholder="전략명, 본문, 종목명 검색"
        @keyup.enter="search"
      /><select v-model="scope">
        <option value="">전체 구분</option>
        <option value="GENERAL">전체 전략</option>
        <option value="STOCK">종목 중심</option></select
      ><select v-model="category">
        <option value="">전체 분류</option>
        <option v-for="c in strategyCategories" :key="c[0]" :value="c[0]">
          {{ c[1] }}
        </option></select
      ><select v-model="status">
        <option value="">전체 상태</option>
        <option value="DRAFT">작성 중</option>
        <option value="ACTIVE">진행 중</option>
        <option value="PAUSED">보류</option>
        <option value="CLOSED">종료</option></select
      ><select v-model="sort">
        <option value="updated">최근 활동순</option>
        <option value="created">최초 작성순</option>
        <option value="title">전략명순</option></select
      ><button class="primary" @click="search">조회</button
      ><button class="secondary" @click="reset">초기화</button>
    </section>
    <p v-if="error" class="strategy-alert">{{ error }}</p>
    <section class="strategy-card table-card">
      <DataTable
        :value="rows"
        :loading="loading"
        data-key="strategyId"
        paginator
        :lazy="true"
        :rows="20"
        :total-records="total"
        :first="page * 20"
        @page="
          page = $event.page;
          load();
        "
        @row-click="router.push(`/strategies/${$event.data.strategyId}`)"
        table-style="min-width: 980px"
        ><Column header="전략"
          ><template #body="{ data }"
            ><button
              class="title-link"
              @click.stop="router.push(`/strategies/${data.strategyId}`)"
            >
              {{ data.title }}</button
            ><small
              >V{{ data.versionNo }} ·
              {{
                data.strategyScope === "STOCK" ? "종목 중심" : "전체 전략"
              }}</small
            ></template
          ></Column
        ><Column header="분류"
          ><template #body="{ data }"
            ><div class="chips">
              <span v-for="code in data.categoryCodes" :key="code">{{
                categoryName(code)
              }}</span>
            </div></template
          ></Column
        ><Column header="상태"
          ><template #body="{ data }"
            ><span class="status" :class="data.status.toLowerCase()">{{
              statusLabel[data.status]
            }}</span></template
          ></Column
        ><Column header="관련 종목"
          ><template #body="{ data }">{{
            data.stocks
              .map((s: StrategySummary["stocks"][number]) => s.stockName)
              .join(", ") || "—"
          }}</template></Column
        ><Column header="최근 활동"
          ><template #body="{ data }">{{
            date(data.latestActivityAt)
          }}</template></Column
        ><Column header="관리"
          ><template #body="{ data }"
            ><div class="actions">
              <button
                class="secondary"
                @click.stop="router.push(`/strategies/${data.strategyId}/edit`)"
              >
                수정</button
              ><button class="danger" @click.stop="remove(data)">삭제</button>
            </div></template
          ></Column
        ><template #empty
          ><div class="empty">등록된 투자전략이 없습니다.</div></template
        ></DataTable
      >
    </section>
  </div>
</template>
<style scoped src="../assets/strategy.css"></style>
