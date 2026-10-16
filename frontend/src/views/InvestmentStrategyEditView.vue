<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  investmentStrategyApi,
  strategyCategories,
  type RichContent,
  type StrategyPayload,
} from "../services/investmentStrategyApi";
import { stockTradeApi, type Stock } from "../services/stockTradeApi";
import StrategyEditor from "../components/strategy/StrategyEditor.vue";
const route = useRoute(),
  router = useRouter(),
  id = computed(() =>
    route.params.strategyId ? Number(route.params.strategyId) : null,
  ),
  title = ref(""),
  scope = ref<"GENERAL" | "STOCK">("GENERAL"),
  status = ref<StrategyPayload["status"]>("DRAFT"),
  categories = ref<string[]>([]),
  content = ref<RichContent>({ type: "doc", content: [] }),
  stocks = ref<Stock[]>([]),
  stockKeyword = ref(""),
  selected = ref<number[]>([]),
  loading = ref(!!id.value),
  saving = ref(false),
  error = ref("");
const filtered = computed(() => {
  const q = stockKeyword.value.trim().toLowerCase();
  return stocks.value.filter(
    (s) =>
      !q ||
      s.stockName.toLowerCase().includes(q) ||
      s.stockCode.toLowerCase().includes(q),
  );
});
onMounted(async () => {
  try {
    stocks.value = await stockTradeApi.getStocks();
    if (id.value) {
      const s = await investmentStrategyApi.detail(id.value);
      title.value = s.title;
      scope.value = s.strategyScope;
      status.value = s.status;
      categories.value = [...s.categoryCodes];
      content.value = s.content;
      selected.value = s.stocks.map((x) => x.stockId);
    }
  } catch (e) {
    error.value =
      e instanceof Error ? e.message : "정보를 불러오지 못했습니다.";
  } finally {
    loading.value = false;
  }
});
async function save() {
  if (!title.value.trim()) {
    error.value = "전략명을 입력해 주세요.";
    return;
  }
  if (scope.value === "STOCK" && !selected.value.length) {
    error.value = "종목 중심 전략은 관련 종목을 선택해 주세요.";
    return;
  }
  saving.value = true;
  error.value = "";
  try {
    const payload: StrategyPayload = {
      strategyScope: scope.value,
      categoryCodes: categories.value,
      title: title.value.trim(),
      content: content.value,
      status: status.value,
      stockIds: selected.value,
    };
    const result = id.value
      ? await investmentStrategyApi.update(id.value, payload)
      : await investmentStrategyApi.create(payload);
    router.push(`/strategies/${result.strategyId}`);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "저장하지 못했습니다.";
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <div class="strategy-page">
    <header class="strategy-head">
      <div>
        <p>INVESTMENT STRATEGY</p>
        <h1>{{ id ? "투자전략 수정" : "투자전략 등록" }}</h1>
        <span>현재 유효한 투자 원칙과 실행 계획을 작성합니다.</span>
      </div>
    </header>
    <p v-if="error" class="strategy-alert">{{ error }}</p>
    <p v-if="loading" class="empty">불러오는 중입니다.</p>
    <section v-else class="strategy-card strategy-form">
      <div class="form-grid">
        <label class="wide"
          ><span class="required-label"><b>*</b> 전략명</span
          ><input
            v-model="title"
            maxlength="200"
            placeholder="전략명을 입력하세요" /></label
        ><label
          >전략 구분<select v-model="scope">
            <option value="GENERAL">전체 전략</option>
            <option value="STOCK">종목 중심 전략</option>
          </select></label
        ><label
          >진행 상태<select v-model="status">
            <option value="DRAFT">작성 중</option>
            <option value="ACTIVE">진행 중</option>
            <option value="PAUSED">보류</option>
            <option value="CLOSED">종료</option>
          </select></label
        >
      </div>
      <fieldset>
        <legend>전략 분류</legend>
        <label v-for="c in strategyCategories" :key="c[0]" class="check"
          ><input v-model="categories" type="checkbox" :value="c[0]" />
          {{ c[1] }}</label
        >
      </fieldset>
      <div class="stock-picker">
        <label>관련 종목</label
        ><input
          v-model="stockKeyword"
          placeholder="종목코드 또는 종목명 검색"
        />
        <div class="stock-options">
          <label v-for="stock in filtered" :key="stock.stockId" class="check"
            ><input
              v-model="selected"
              type="checkbox"
              :value="stock.stockId"
            /><span
              ><strong>{{ stock.stockName }}</strong
              ><small
                >{{ stock.stockCode }} ·
                {{ stock.marketCode || "시장 미지정" }}</small
              ></span
            ></label
          >
        </div>
      </div>
      <label class="body-label">투자전략 본문</label
      ><StrategyEditor v-model="content" />
      <footer>
        <button
          class="secondary"
          @click="router.push(id ? `/strategies/${id}` : '/strategies')"
        >
          취소</button
        ><button class="primary" :disabled="saving" @click="save">
          {{ saving ? "저장 중..." : "저장" }}
        </button>
      </footer>
    </section>
  </div>
</template>
<style scoped src="../assets/strategy.css"></style>
