# SPEC-003: 프론트엔드 테이블 PrimeVue DataTable 전환

## 1. 목적

프론트엔드의 모든 화면에서 데이터 목록을 표 형태로 표시할 때 PrimeVue `DataTable`을 사용하도록 통일한다. 현재 일반 HTML `<table>`을 사용하는 투자계좌 목록과 거래 내역 목록을 PrimeVue `DataTable`로 전환하며, 기존 검색, 서버 페이징, 행 동작 및 표시 데이터는 유지한다.

## 2. 현재 구현 구조

### Frontend

- 현재 라우트 기준 화면은 `StockTradeView.vue`, `AccountSettingsView.vue`, `HomeView.vue`이다. 이 중 일반 HTML `<table>`을 사용하는 화면은 앞의 두 화면뿐이며, `HomeView.vue`에는 표 형식의 데이터 목록이 없다.
- `AccountSettingsView.vue`의 좌측 증권사 목록에서 `selectedBrokerageId`를 선택하면 `loadAccounts()`가 `GET /api/master/investment-accounts`를 호출한다. 투자계좌 목록은 일반 HTML `<table>`이며, 계좌 별칭, 마스킹된 계좌번호, 최신 메모, 사용 상태, 수정/상태 변경 동작을 표시한다.
- 계좌 목록은 `accounts`, `loadingAccounts`, `accountTotal`, `accountPage`, `accountPages`, `pageSize(20)` 상태와 별도 `<nav class="pager">` 및 `changeAccountPage(next)`를 사용한다.
- `StockTradeView.vue`의 거래 내역도 일반 HTML `<table>`이며, 거래일, 구분, 종목, 계좌, 수량, 체결단가, 체결금액, 실현손익, 수정/삭제 동작을 표시한다.
- 거래 목록은 `trades`, `loading`, `page`, `totalElements`, `totalPages`, `pageSize(20)` 상태와 별도 `<nav class="pagination">` 및 `changePage(nextPage)`를 사용한다.
- 두 화면 모두 서버 검색과 `PageResponse` 기반 페이지 조회를 사용한다. 현재 계좌 등록·수정·메모 모달과 거래 등록·수정 모달은 전환 대상이 아니다.
- `frontend/src/main.ts`에서 PrimeVue와 Aura 기반 테마를 전역 설정했고, `frontend/src/assets/tailwind.css`에는 `.p-datatable` 헤더용 공통 스타일이 이미 있다. 두 화면은 아직 `DataTable`과 `Column`을 사용하지 않는다.

### Backend

- 계층은 `InvestmentMasterController` → `InvestmentMasterService` → `InvestmentMasterRepository`이다.
- 목록 API는 `GET /api/master/investment-accounts?brokerageId=&keyword=&useYn=&page=&size=`이다.
- 응답은 `PageResponse<AccountResponse>`이며 `content`, `totalElements`, `totalPages`, `page`, `size`를 포함한다.
- Repository는 `brokerageId`, 계좌 별칭/번호 키워드, 사용 여부로 필터링하고 `LIMIT/OFFSET`을 적용한다. 기본 정렬은 `brokerage_name, account_name` 오름차순이다.
- 목록 응답의 계좌번호는 마스킹되고, `latestMemoContent`는 `investment_account_memo`의 최신 1건을 읽기 전용으로 조인해 제공된다.

### DB 구조

- 프로젝트 리소스에는 애플리케이션 DDL/마이그레이션 파일이 없으므로, 현재 사용 중인 Repository SQL 및 기존 명세를 기준으로 구조를 확인한다.
- `investment_account`는 `account_id`, `brokerage_id`, `account_name`, `account_number`, `use_yn`, `created_at`, `updated_at`을 사용한다.
- `brokerage_company`는 `brokerage_id`로 `investment_account.brokerage_id`와 연결된다.
- `investment_account_memo`는 `memo_id`, `account_id`, `memo_content`, `created_at`을 사용한다. 목록의 최신 메모는 `created_at DESC, memo_id DESC` 기준 1건이다.

## 3. 요구사항

1. 프론트엔드의 모든 현행 및 이후 화면에서 표 형식 데이터 목록은 PrimeVue `DataTable`을 사용해야 한다.
2. 현행 일반 HTML `<table>`인 투자계좌 정보 리스트와 거래 내역 리스트를 PrimeVue `DataTable`로 전환해야 한다.
3. 각 목록의 현재 표시 열과 행별 동작(계좌 수정/상태 변경, 거래 수정/삭제)을 유지해야 한다.
4. 현재의 서버 측 검색, 상태 필터, 선택 증권사 조건 및 페이지당 20건 정책을 유지해야 한다.
5. `DataTable`의 페이지네이터는 서버 페이징과 연동해야 하며, 전체 데이터를 클라이언트에서 다시 페이징하지 않는다.
6. 계좌 증권사 미선택, 각 목록의 조회 중, 조회 결과 없음 안내를 현재 의미와 동등하게 제공해야 한다.
7. 애플리케이션 소스는 이 명세 승인 후에만 변경한다.

## 4. 변경 대상

| 구분 | 파일 | 변경 내용 |
| --- | --- | --- |
| Frontend 화면 | `frontend/src/views/AccountSettingsView.vue` | 일반 계좌 `<table>` 및 수동 계좌 pager를 PrimeVue `DataTable`/`Column` 및 내장 paginator로 교체 |
| Frontend 화면 | `frontend/src/views/StockTradeView.vue` | 일반 거래 `<table>` 및 수동 거래 pager를 PrimeVue `DataTable`/`Column` 및 내장 paginator로 교체 |
| Frontend 공통 스타일 | `frontend/src/assets/tailwind.css` | 두 DataTable의 공통 스타일 누락 또는 충돌이 확인될 때만 최소 보완 |
| Frontend API | `frontend/src/services/investmentMasterApi.ts` | 변경 없음 |
| Backend | Controller, Service, Repository, DTO | 변경 없음 |
| DB | `investment_account`, `brokerage_company`, `investment_account_memo` | 변경 없음 |

## 5. Backend 변경

Backend 변경은 없다.

- 기존 계좌 및 거래 목록 API와 `PageResponse` 계약을 그대로 사용한다.
- 현재의 `page`(0부터 시작), `size`, `totalElements`, `totalPages` 의미를 변경하지 않는다.
- 검색 조건, 정렬, 최신 메모 조회 방식, 계좌번호 마스킹, 계좌 수정/사용 상태 변경 및 거래 수정/삭제 API를 변경하지 않는다.

## 6. Frontend 변경

### 6.1 컴포넌트 및 데이터 연결

- `AccountSettingsView.vue`의 `<script setup>`에 PrimeVue `DataTable`과 `Column`을 명시적으로 import한다. 전역 등록에 의존하지 않는다.
- `DataTable`에는 `accounts`를 `value`로, `accountId`를 `dataKey`로 전달한다.
- `loadingAccounts`를 `loading`에 연결한다.
- `paginator`, `lazy`, `rows=pageSize`, `first=accountPage * pageSize`, `totalRecords=accountTotal`을 설정해 현재 서버 페이징 상태를 표현한다.
- `@page` 이벤트는 PrimeVue 이벤트의 대상 페이지를 받아 `accountPage`를 갱신한 뒤 기존 `loadAccounts()`를 호출한다. `rows`는 고정값 20으로 유지한다.
- DataTable은 `totalRecords`만 사용하므로 계좌 목록 전용 `accountPages` 상태, 응답의 `totalPages` 대입, `changeAccountPage()`와 계좌 영역의 별도 `<nav class="pager">`는 제거한다. 증권사 목록의 pager와 `changeBrokeragePage()`는 유지한다.

### 6.2 열 및 슬롯

- 다음 `Column` 구성을 유지한다.
  - 계좌 별칭: `accountName`, 강조 표시
  - 계좌번호: `accountNumber`, 없으면 `—`
  - 메모: `latestMemoContent`, 없으면 `—`, 현재와 같은 한 줄 말줄임 처리
  - 상태: `useYn` 값으로 기존 `status`/`status off` 표현
  - 동작: 수정 및 사용 상태 변경 버튼
- 행 동작은 현행과 동일하게 `openAccountEdit(item)` 및 `toggleAccount(item)`을 호출한다.
- `#empty` 및 필요 시 `#loading` 슬롯으로 다음 상태를 분리해 표시한다.
  - 증권사 미선택: 증권사 선택 안내
  - 선택된 증권사의 계좌 없음: 계좌 등록 안내
  - 로딩: DataTable loading 상태 또는 동등한 로딩 안내
- DataTable 내부에서 행 슬롯/열 body 슬롯을 사용할 때도 버튼의 기본 submit 동작이 발생하지 않도록 모든 행 동작 버튼에 `type="button"`을 유지 또는 추가한다.

### 6.3 검색 및 상태 동기화

- `selectBrokerage()`는 현행처럼 계좌 페이지를 0으로 초기화하고 계좌 검색어 및 사용 상태 필터를 초기화한 뒤 목록을 다시 조회한다.
- `searchAccounts()`는 페이지를 0으로 초기화한 뒤 DataTable에 전달되는 `first`와 서버 요청 모두 첫 페이지가 되도록 한다.
- 등록, 수정, 메모 등록, 사용 상태 변경 후 실행되는 현행 `loadAccounts()` 호출을 유지한다. 갱신 후 현재 페이지가 유효한 경우 그 페이지를 유지한다.
- 계좌 목록 조회 실패 시 기존 `errorMessage` 처리 방식을 유지한다.

### 6.4 스타일

- `account-table-wrap`는 DataTable의 가로 스크롤/반응형 영역을 수용하도록 최소 조정한다.
- 기존 일반 테이블 전용 선택자(`table`, `th`, `td`)와 계좌 수동 pager 전용 스타일은 DataTable에 필요한 범위로 정리한다. 증권사 목록의 pager 스타일은 변경하지 않는다.
- 이미 존재하는 전역 `.p-datatable` 헤더 테마를 우선 사용하며, 이 화면에 국한된 셀 여백, 메모 말줄임, 행 동작 정렬, 빈 상태 스타일만 scoped 스타일로 보완한다.

### 6.5 거래 내역 DataTable 전환

- `StockTradeView.vue`의 `<script setup>`에도 PrimeVue `DataTable`과 `Column`을 명시적으로 import한다.
- `DataTable`에는 `trades`를 `value`로, `tradeId`를 `dataKey`로 전달하고 `loading`을 연결한다.
- `paginator`, `lazy`, `rows=pageSize`, `first=page * pageSize`, `totalRecords=totalElements`를 설정한다.
- `@page` 이벤트는 이벤트의 대상 페이지로 `page`를 갱신하고 기존 `loadTrades()`를 호출한다. 거래 목록 역시 페이지 크기는 20으로 고정한다.
- DataTable은 `totalRecords`만 사용하므로 거래 목록 전용 `totalPages` 상태, 응답의 `totalPages` 대입, `changePage()`와 거래 영역의 별도 `<nav class="pagination">`는 제거한다. 삭제 후 마지막 행이 사라진 페이지를 보정하는 기존 로직은 `page > 0` 및 현재 행 수 조건을 유지한 채 `page`를 감소시키고 재조회한다.
- 다음 `Column` 구성을 유지한다.
  - 거래일: `tradeDate`
  - 구분: 매수/매도 배지
  - 종목: 종목명, 종목코드, 시장 코드
  - 계좌: 계좌 별칭, 증권사명
  - 수량, 체결단가, 체결금액, 실현손익: 기존 `formatNumber`, `displayCurrency`, 수익/손실 색상 및 숫자 정렬
  - 동작: 수정 및 삭제 버튼
- 각 행 동작은 현재와 동일하게 `openEditForm(trade)` 및 `deleteTrade(trade)`를 호출하고, 버튼에는 `type="button"`을 유지한다.
- `#empty` 및 필요 시 `#loading` 슬롯으로 현재의 조회 중과 빈 거래 목록 안내 및 거래 등록 버튼을 유지한다.
- `table-wrap`, 일반 거래 테이블 전용 선택자와 거래 수동 pagination 전용 스타일을 DataTable 구조에 맞게 최소 정리한다. 기존 거래 카드, 필터, 요약 카드, 모달 스타일은 변경하지 않는다.

### 6.6 이후 화면의 테이블 규칙

- 이후 추가하거나 수정하는 모든 프론트엔드 화면에서 데이터 행·열을 가진 목록은 일반 `<table>` 대신 PrimeVue `DataTable`과 `Column`으로 구현한다.
- 카드형 목록, 폼 레이아웃, 비교 목적이 아닌 단순 레이아웃용 HTML 요소에는 DataTable을 강제하지 않는다.
- 데이터가 서버 페이징 API를 사용하는 경우에는 이 명세와 동일하게 `lazy` DataTable로 연동한다. 전체 데이터를 이미 화면에서 보유하며 클라이언트 페이징이 명시적으로 요구되는 경우에만 비-lazy 모드를 사용한다.

## 7. DB 변경

DB 변경은 없다.

- 테이블, 컬럼, 제약조건, 인덱스, 데이터 및 마이그레이션을 추가·변경·삭제하지 않는다.
- DataTable 전환은 기존 계좌·거래 목록 조회 결과의 렌더링 방식만 바꾸며, `investment_account`, `investment_account_memo`, 거래 관련 테이블 및 SQL에 영향을 주지 않는다.

## 8. 기존 동작 유지 조건

- 증권사 등록·수정·사용 상태 변경·검색·수동 pager 동작은 유지한다.
- 계좌 등록·수정 모달, 계좌 메모 이력 조회·등록 흐름은 유지한다.
- 거래 검색·초기화, 기준정보 연동, 거래 등록·수정·삭제 모달 및 삭제 확인 흐름은 유지한다.
- 계좌 목록은 선택한 증권사에만 한정되며, 증권사 변경 시 계좌 검색 조건을 초기화한다.
- 계좌 검색은 별칭 또는 계좌번호를 대상으로 하고, 사용 상태 필터도 기존과 동일하게 적용한다.
- 계좌번호 목록 마스킹, 최신 메모 한 건 표시, 메모 부재 시 `—`, 상태 문구(사용 중/중지)는 유지한다.
- 목록 정렬·페이지 크기·API URL·요청 파라미터·응답 타입은 변경하지 않는다.
- 계좌 목록의 기본 정렬과 거래 목록의 기존 서버 정렬을 유지하고, DataTable은 목록 표시와 페이징만 담당한다. 클라이언트 정렬·필터링·전체 데이터 선로딩을 추가하지 않는다.

## 9. 구현 제약

- 이 명세 자체 외 애플리케이션 소스는 현재 단계에서 수정하지 않는다.
- PrimeVue 4의 현재 설치 의존성을 사용하며, 패키지 추가·업그레이드·전역 테마 변경을 하지 않는다.
- PrimeVue `DataTable`과 `Column`만 필요한 범위로 도입한다. 별도 테이블 래퍼, 새 라우트, 새 API, 새 상태 관리 라이브러리를 추가하지 않는다.
- 계좌 및 거래 목록 API가 서버 페이징이므로 `lazy` 모드를 사용한다. DataTable 기본 클라이언트 페이징/정렬 결과에 서버 응답을 혼합하지 않는다.
- 프론트엔드 소스의 표 형식 목록에 새로운 일반 HTML `<table>`을 추가하지 않는다.
- 기존 작업 트리에 이미 있는 애플리케이션 변경사항은 이 작업의 변경 대상으로 취급하거나 덮어쓰지 않는다.

## 10. 검증 방법

### 정적 및 빌드 검증

1. `frontend`에서 `npm run type-check` 또는 `npm run build`를 실행해 Vue 템플릿, PrimeVue import, 두 DataTable 이벤트 타입 오류가 없는지 확인한다.
2. Git diff로 애플리케이션 변경 범위가 `AccountSettingsView.vue`, `StockTradeView.vue` 및 필요 시 최소 스타일 파일에 한정되고, backend·DB 관련 파일이 변경되지 않았는지 확인한다.
3. `frontend/src`에 표 형식 데이터 목록을 위한 일반 HTML `<table>` 또는 해당 목록의 수동 pager 참조가 남지 않았는지 확인한다.

### 수동 기능 검증

1. 계좌 설정 화면에서 투자계좌 목록이 PrimeVue DataTable 헤더·행·내장 페이지네이터로 표시되는지 확인한다.
2. 증권사를 선택하지 않았을 때 안내 상태가 표시되고, 선택 후 해당 증권사의 계좌만 표시되는지 확인한다.
3. 검색어와 사용 상태를 각각 또는 함께 적용했을 때 첫 페이지부터 서버 결과·전체 건수·DataTable 페이지네이터가 일치하는지 확인한다.
4. DataTable 페이지 이동 시 올바른 `page`와 `size=20`으로 조회하고, 다음 페이지의 데이터가 표시되는지 확인한다.
5. 각 행에서 수정과 사용 상태 변경을 실행했을 때 기존 모달과 API 동작이 그대로 동작하고 목록이 갱신되는지 확인한다.
6. 계좌가 없는 증권사, 로딩 중, API 실패, 최신 메모가 없는 계좌에서 각각 기존 의미에 맞는 상태와 표시가 유지되는지 확인한다.
7. 계좌번호 마스킹과 최신 메모 말줄임이 기존과 동등하게 유지되는지 확인한다.
8. 거래 내역 화면에서 목록이 PrimeVue DataTable 헤더·행·내장 페이지네이터로 표시되는지 확인한다.
9. 거래 검색·초기화 후 첫 페이지가 표시되고, DataTable 페이지 이동 시 올바른 `page`와 `size=20`으로 재조회되는지 확인한다.
10. 거래 행의 매수/매도 배지, 숫자·통화 포맷, 수익/손실 색상, 수정·삭제 및 삭제 확인 흐름이 기존과 동일한지 확인한다.
11. 거래 목록의 로딩·빈 상태와 거래 등록 버튼이 유지되며, 좁은 화면에서 DataTable의 가로 스크롤로 모든 열과 행 동작에 접근 가능한지 확인한다.
