# SPEC-004: 주식종목 기준정보 관리 및 거래 종목 선택

## 1. 목적

`stock_master`를 기준으로 주식종목을 등록·조회·수정·삭제하고 사용 여부를 관리하는 프론트엔드와 백엔드 구현 방안을 정의한다. 거래 등록·수정 화면에서는 종목명이나 종목코드를 직접 입력하지 않고, 돋보기 버튼으로 종목 기준정보를 조회한 뒤 한 건을 선택하여 `stockId`를 설정하도록 변경한다.

이 문서는 구현 전 분석·설계 명세이며, 작성 단계에서는 애플리케이션 소스와 DB를 변경하지 않는다.

## 2. 현재 구현 구조

### 2.1 Backend

- 백엔드는 Spring Boot 3.2.4, Java 21, PostgreSQL 및 `NamedParameterJdbcTemplate`을 사용한다.
- 거래 기능은 `StockTradeController` → `StockTradeService` → `StockTradeRepository` 구조이다.
- `StockTradeController`는 `/api`를 기준 경로로 사용하며 거래 CRUD 외에 거래 화면용 기준정보 API를 함께 제공한다.
  - `GET /api/brokerages`: 사용 중인 증권사 목록
  - `GET /api/investment-accounts`: 사용 중인 계좌 목록
  - `GET /api/stocks?keyword=&marketCode=`: 사용 중인 종목 목록
- 현재 종목 조회는 `stock_master.use_yn = TRUE`만 대상으로 종목코드·종목명 부분 검색과 시장코드 필터를 적용하고, 종목명 오름차순으로 최대 200건을 반환한다.
- 거래 생성·수정 요청은 `stock_id`를 받고, `activeStockExists()`로 사용 중인 종목인지 검증한다.
- 거래 목록과 상세는 `stock_trade.stock_id`로 `stock_master`를 조인하여 종목코드, 종목명, 시장코드, 통화코드를 표시한다.
- 기준정보 관리 기능은 `master` 패키지의 `InvestmentMasterController` → `InvestmentMasterService` → `InvestmentMasterRepository` 패턴으로 구현되어 있다.
  - 관리 API 기준 경로는 `/api/master`이다.
  - 목록은 `content`, `totalElements`, `totalPages`, `page`, `size`를 가진 서버 페이징 응답을 사용한다.
  - 입력 정규화·필수값·길이·중복·상위 기준정보 상태 검증은 Service에서 수행한다.
  - Repository가 SQL, `LIMIT/OFFSET`, `CURRENT_TIMESTAMP` 기반 수정 일시 갱신을 담당한다.
- `ApiExceptionHandler`는 업무 오류를 상태 코드와 `message`가 포함된 JSON으로 반환하고, DB 무결성 위반은 HTTP 409로 처리한다.

### 2.2 Frontend

- 프론트엔드는 Vue 3, TypeScript, Vue Router, PrimeVue 4를 사용한다.
- 공통 셸은 `AppShell.vue`이며 현재 메뉴는 거래 내역, 증권사·계좌 관리, 서버 연결 확인으로 구성된다.
- `AccountSettingsView.vue`가 기준정보 관리용 기본 페이지 역할을 한다.
  - 검색·상태 필터, 서버 페이징, PrimeVue `DataTable`, 등록·수정·상세 모달, 행별 관리 버튼, 오류 알림과 완료 토스트 패턴을 제공한다.
  - 전역 `tailwind.css`가 공통 색상, 입력 요소, 버튼, DataTable, 모달과 상태 배지 스타일을 보완한다.
- `StockTradeView.vue`는 마운트 시 증권사·계좌·활성 종목을 모두 조회한다.
- 거래 등록·수정 모달의 종목 필드는 현재 자유 입력 `<input>`과 `<datalist>` 조합이다.
  - 사용자가 입력한 값과 로드된 최대 200건의 종목을 종목코드/종목명으로 비교한다.
  - 정확히 일치하거나 부분 일치 결과가 한 건이면 `form.stockId`를 설정한다.
  - 일치하지 않거나 복수 부분 일치이면 저장 시 오류가 발생한다.
- 프론트엔드 종목 타입과 거래 API 호출은 `frontend/src/services/stockTradeApi.ts`에 정의되어 있으며, 별도의 종목 관리 API 모듈과 종목 관리 라우트는 없다.

### 2.3 DB 구조

- 사용자 제공 DDL 기준 `public.stock_master`는 다음 구조이다.

| 컬럼 | 타입 | 필수 | 기본값/의미 |
| --- | --- | --- | --- |
| `stock_id` | `bigserial` | 예 | 기본키 |
| `stock_code` | `varchar(30)` | 예 | 종목코드 |
| `stock_name` | `varchar(200)` | 예 | 종목명 |
| `market_code` | `varchar(30)` | 아니오 | 시장코드 |
| `currency_code` | `varchar(10)` | 예 | 기본값 `KRW` |
| `use_yn` | `bool` | 예 | 기본값 `true` |
| `created_at` | `timestamp` | 예 | 기본값 `CURRENT_TIMESTAMP` |
| `updated_at` | `timestamp` | 예 | 기본값 `CURRENT_TIMESTAMP` |

- 기본키는 `stock_id`이고 `(stock_code, market_code)`에 `uq_stock_master` 유일 제약조건이 있다.
- 프로젝트 안에는 이 테이블을 생성하는 DDL/마이그레이션 파일이 없다. 본 명세에서는 사용자 제공 DDL과 현재 Repository SQL을 DB 구조의 기준으로 사용한다.
- `stock_trade`는 현재 코드상 `stock_id`를 저장하고 `stock_master`와 조인한다. 제공된 DDL에는 `stock_trade`의 외래키 정의가 포함되어 있지 않으므로 DB의 실제 삭제 제약조건은 확인되지 않았다. 구현에서는 DB 외래키 유무에 의존하지 않고 참조 거래 존재 여부를 명시적으로 검사한다.
- PostgreSQL의 일반 UNIQUE 제약조건은 `NULL`을 서로 다른 값으로 취급하므로, 현재 DDL만으로는 `market_code IS NULL`인 동일 `stock_code`의 중복을 완전히 막지 못한다. DB 변경 없이 Service/Repository의 null-safe 중복 검사로 보완한다.

## 3. 요구사항

1. 주식종목 기준정보를 목록 조회, 등록, 수정, 삭제할 수 있어야 한다.
2. 종목코드, 종목명, 시장코드, 통화코드, 사용 여부를 관리해야 한다.
3. 종목 목록은 종목코드 또는 종목명 검색, 시장코드 및 사용 상태 필터, 서버 페이징을 지원해야 한다.
4. 종목 관리 화면은 기존 기본 관리 페이지인 `AccountSettingsView.vue`와 공통 셸의 디자인·상호작용 패턴을 따른다.
5. 사이드바에서 종목 관리 화면으로 이동할 수 있어야 한다.
6. `StockTradeView.vue`의 거래 등록·수정 모달에서는 종목을 직접 입력할 수 없어야 한다.
7. 거래 종목 영역의 돋보기 버튼을 누르면 활성 종목 기준정보를 조회하는 선택 모달을 열어야 한다.
8. 선택 모달에서 종목코드 또는 종목명과 시장코드로 검색하고 한 건을 선택하면 거래 폼에 종목 식별자와 표시 정보가 반영되어야 한다.
9. 거래 저장 요청 계약은 기존처럼 `stockId`를 사용해야 한다.
10. 사용 중지 종목은 신규 거래의 선택 결과에서 제외하되, 기존 거래 목록에는 계속 표시되어야 한다.
11. 기존 거래가 참조하는 종목을 삭제하여 거래 이력을 훼손해서는 안 된다.
12. 관련 DB 테이블은 이미 존재하므로 스키마 생성이나 변경 없이 구현해야 한다.

## 4. 변경 대상

| 구분 | 파일 | 변경 내용 |
| --- | --- | --- |
| Backend 신규 | `backend/src/main/java/com/example/backend/master/StockMasterController.java` | 종목 관리 REST API |
| Backend 신규 | `backend/src/main/java/com/example/backend/master/StockMasterDtos.java` | 요청·응답·페이지 DTO |
| Backend 신규 | `backend/src/main/java/com/example/backend/master/StockMasterService.java` | 정규화, 검증, CRUD, 삭제 정책 |
| Backend 신규 | `backend/src/main/java/com/example/backend/master/StockMasterRepository.java` | `stock_master` 조회·저장 SQL 및 참조 검사 |
| Backend 기존 | `backend/src/main/java/com/example/backend/trade/StockTradeController.java` | 기존 활성 종목 선택 API 계약 유지; 필요 시 조회 한도/파라미터 설명만 보완 |
| Backend 기존 | `backend/src/main/java/com/example/backend/trade/StockTradeService.java` | 기존 활성 종목 조회·거래 저장 검증 유지 |
| Backend 기존 | `backend/src/main/java/com/example/backend/trade/StockTradeRepository.java` | 기존 활성 종목 검색 재사용; 선택 모달 요구에 필요한 최소 조회 조정만 허용 |
| Backend 테스트 신규 | `backend/src/test/java/com/example/backend/master/StockMasterServiceTest.java` | 종목 검증·중복·삭제 정책 단위 테스트 |
| Frontend 신규 | `frontend/src/services/stockMasterApi.ts` | 관리 목록 및 CRUD API 타입·호출 함수 |
| Frontend 신규 | `frontend/src/views/StockSettingsView.vue` | 종목 관리 목록·검색·등록·수정·삭제 화면 |
| Frontend 기존 | `frontend/src/services/stockTradeApi.ts` | 종목 조회에 `keyword`, `marketCode` 전달 지원 |
| Frontend 기존 | `frontend/src/views/StockTradeView.vue` | 직접 입력 제거 및 돋보기 종목 선택 모달 추가 |
| Frontend 기존 | `frontend/src/router/index.ts` | `/settings/stocks` 라우트 추가 |
| Frontend 기존 | `frontend/src/views/AppShell.vue` | `주식종목 관리` 메뉴 추가 |
| Frontend 스타일 | `frontend/src/assets/tailwind.css` | 기존 공통 스타일로 부족한 부분만 최소 보완 |
| DB | 변경 파일 없음 | 기존 `stock_master`, `stock_trade` 사용 |

## 5. Backend 변경

### 5.1 관리 API

관리 API는 기존 기준정보 경로 규칙을 따라 `/api/master/stocks`를 사용한다.

| Method | 경로 | 기능 | 정상 응답 |
| --- | --- | --- | --- |
| `GET` | `/api/master/stocks` | 관리 목록 검색·페이징 | `200 PageResponse<StockResponse>` |
| `GET` | `/api/master/stocks/{stockId}` | 종목 단건 조회 | `200 StockResponse` |
| `POST` | `/api/master/stocks` | 종목 등록 | `201 StockResponse`, `Location` 헤더 |
| `PUT` | `/api/master/stocks/{stockId}` | 종목 전체 수정 | `200 StockResponse` |
| `PATCH` | `/api/master/stocks/{stockId}/usage` | 사용 상태 변경 | `200 StockResponse` |
| `DELETE` | `/api/master/stocks/{stockId}` | 미사용 종목 물리 삭제 | `204` |

- 목록 파라미터는 `keyword`, `marketCode`, `useYn`, `page`, `size`로 한다.
- `keyword`는 대소문자를 구분하지 않고 `stock_code`, `stock_name` 부분 일치를 검색한다.
- `marketCode`는 앞뒤 공백을 제거한 정확 일치로 필터링한다.
- `page`는 0부터 시작하고 음수는 0으로 보정한다. `size` 기본값은 20이며 1~100으로 제한한다.
- 기본 정렬은 `stock_name`, `stock_code`, `stock_id` 오름차순으로 고정한다. 이번 범위에서 임의 정렬 API는 추가하지 않는다.
- 페이지 응답은 기존 관리 API와 동일하게 `content`, `totalElements`, `totalPages`, `page`, `size`를 반환한다.

### 5.2 DTO

- `StockRequest`
  - `String stockCode`
  - `String stockName`
  - `String marketCode`
  - `String currencyCode`
  - `Boolean useYn`
- `StockResponse`
  - `Long stockId`
  - `String stockCode`
  - `String stockName`
  - `String marketCode`
  - `String currencyCode`
  - `Boolean useYn`
  - `LocalDateTime createdAt`
  - `LocalDateTime updatedAt`
- `UsageRequest(Boolean useYn)`와 `PageResponse<T>`는 종목 관리 모듈에 정의한다. 다른 DTO 클래스의 내부 record에 직접 결합하지 않는다.

### 5.3 입력 정규화 및 검증

- `stockCode`는 필수이며 trim 후 빈 값일 수 없고 30자 이하여야 한다.
- `stockName`은 필수이며 trim 후 빈 값일 수 없고 200자 이하여야 한다.
- `marketCode`는 선택값이며 trim 후 빈 문자열은 `null`로 저장하고, 값이 있으면 30자 이하여야 한다.
- `currencyCode`는 trim 후 빈 값이면 `KRW`를 적용하고 10자 이하여야 한다.
- `useYn`이 누락되면 `true`를 적용한다.
- 종목코드, 시장코드, 통화코드의 대소문자는 사용자가 입력한 값을 보존한다. 검색만 대소문자를 구분하지 않는다.
- 등록·수정 전에 `stock_code`와 `market_code`의 조합 중복을 검사한다. `market_code` 비교는 `IS NOT DISTINCT FROM` 의미로 처리하여 둘 다 `NULL`인 경우도 중복으로 판단한다.
- 수정 시 자기 자신의 `stock_id`는 중복 검사에서 제외한다.
- 중복이면 HTTP 409와 `이미 등록된 종목코드와 시장 조합입니다.` 메시지를 반환한다. 동시 요청으로 DB 유일 제약조건이 먼저 발생하는 경우에도 전역 무결성 오류 처리로 HTTP 409를 유지한다.
- 존재하지 않는 `stockId` 조회·수정·상태 변경·삭제는 HTTP 404를 반환한다.

### 5.4 저장 및 삭제 정책

- 등록은 `stock_code`, `stock_name`, `market_code`, `currency_code`, `use_yn`을 저장하고 DB 기본값으로 생성·수정 일시를 설정한다.
- 수정은 위 관리 필드를 갱신하고 `updated_at = CURRENT_TIMESTAMP`를 명시한다. `created_at`은 변경하지 않는다.
- 사용 상태 변경은 `use_yn`과 `updated_at`만 갱신한다.
- 삭제 전 `stock_trade`에 같은 `stock_id`가 존재하는지 `EXISTS`로 확인한다.
- 참조 거래가 있으면 종목과 거래를 모두 보존하고 HTTP 409와 `거래 내역에서 사용 중인 종목은 삭제할 수 없습니다. 사용 중지로 변경해 주세요.` 메시지를 반환한다.
- 참조 거래가 없는 경우에만 `stock_master` 행을 물리 삭제한다.
- 종목 삭제 과정에서 `stock_trade`를 연쇄 삭제하거나 수정하지 않는다.

### 5.5 거래용 종목 조회 연동

- 기존 `GET /api/stocks?keyword=&marketCode=`는 거래 선택 전용 API로 유지한다.
- 이 API는 `use_yn = TRUE`인 종목만 반환하고, 관리 화면의 비활성 종목은 노출하지 않는다.
- 응답 구조는 기존 `StockResponse(stockId, stockCode, stockName, marketCode, currencyCode)`를 유지한다.
- 돋보기 선택 모달은 검색 실행 시 이 API를 호출한다. 현재 최대 200건 제한은 유지하며, 200건을 초과할 수 있으므로 사용자가 종목코드·종목명 또는 시장코드로 결과를 좁힐 수 있게 한다.
- 거래 생성·수정 시 `activeStockExists(stockId)` 서버 검증을 유지하여 UI 우회를 차단한다.

## 6. Frontend 변경

### 6.1 종목 관리 API 모듈

- `stockMasterApi.ts`에 다음 타입을 정의한다.
  - `PageResponse<T>`
  - `ManagedStock`
  - `StockPayload`
- 다음 함수를 제공한다.
  - `getStocks(params)`
  - `getStock(id)`
  - `createStock(payload)`
  - `updateStock(id, payload)`
  - `updateStockUsage(id, useYn)`
  - `deleteStock(id)`
- 오류 응답의 `message`를 표시하는 방식은 기존 `investmentMasterApi.ts`와 동일하게 구현한다.

### 6.2 종목 관리 화면

- `StockSettingsView.vue`를 새로 만들고 `AccountSettingsView.vue` 및 전역 기본 테마를 디자인 기준으로 사용한다.
- 페이지 제목은 `주식종목 관리`, 설명은 거래 등록에 사용할 종목 기준정보를 관리한다는 의미로 표시한다.
- 상단에 다음 검색 조건과 동작을 제공한다.
  - 종목코드 또는 종목명 키워드
  - 시장코드
  - 상태: 전체/사용 중/사용 중지
  - 조회, 초기화, 종목 등록
- 목록은 PrimeVue `DataTable`의 `lazy` 서버 페이징으로 구현한다.
  - `dataKey="stockId"`
  - 페이지 크기 20
  - 전체 건수, 로딩 상태, 빈 결과 상태 표시
  - 키워드·시장·상태 변경 후 조회할 때 첫 페이지로 이동
- 목록 열은 종목코드, 종목명, 시장코드, 통화코드, 상태, 수정일시, 관리로 구성한다.
- 관리 열은 수정, 사용 상태 변경, 삭제 버튼을 제공한다.
- 등록·수정은 동일한 모달 폼을 사용한다.
  - 종목코드: 필수, `maxlength=30`
  - 종목명: 필수, `maxlength=200`
  - 시장코드: 선택, `maxlength=30`
  - 통화코드: 필수, `maxlength=10`, 등록 기본값 `KRW`
  - 사용 여부 체크박스: 등록 기본값 `true`
- 저장 중 중복 전송을 막고, 성공 시 모달을 닫고 현재 조건의 목록을 다시 조회하며 완료 토스트를 표시한다.
- 삭제 전에 종목명과 종목코드를 포함한 확인 메시지를 표시한다.
- 삭제 성공 후 현재 페이지의 마지막 행이 없어졌으면 앞 페이지로 이동한 뒤 재조회한다.
- 참조 거래로 삭제가 거부된 경우 API의 HTTP 409 메시지를 그대로 사용자에게 보여 주고 목록은 유지한다.
- 사용 상태 변경은 별도 확인 후 PATCH API를 호출한다. 사용 중지된 종목은 관리 목록에는 남고 거래 종목 선택창에서만 제외된다.

### 6.3 라우트와 메뉴

- `router/index.ts`에 다음 자식 라우트를 추가한다.
  - 경로: `/settings/stocks`
  - 이름: `stock-settings`
  - 컴포넌트: `StockSettingsView.vue`
  - 페이지 제목: `주식종목 관리`
- `AppShell.vue`의 `투자 관리` 그룹에 `주식종목 관리` 메뉴를 추가한다.
- 아이콘은 현재 설치된 PrimeIcons의 종목 관리 의미에 맞는 아이콘을 사용하고, 기존 메뉴 순서와 활성 표시 방식을 유지한다.

### 6.4 거래 화면 종목 직접 입력 제거

- `StockTradeView.vue`에서 다음 기존 요소와 로직을 제거한다.
  - `stockInput` 자유 입력 상태
  - `<input list="stock-options">`와 `<datalist>`
  - `normalizeStockText()`와 `selectStockFromInput()`
  - 입력 문자열을 로컬 최대 200건과 비교하여 종목을 추론하는 저장 전 처리
- 거래 폼의 종목 영역은 다음 요소로 교체한다.
  - 선택한 종목의 `종목명 (종목코드) · 시장코드`를 표시하는 읽기 전용 영역
  - 종목이 없을 때 `종목을 선택해 주세요.` 안내
  - 돋보기 아이콘 버튼과 접근 가능한 `aria-label="종목 검색"`
- 사용자는 읽기 전용 표시 영역에 직접 타이핑할 수 없으며, 돋보기 버튼으로만 종목을 변경한다.
- 거래 폼의 실제 값은 계속 `form.stockId`에 저장한다. 선택 종목 표시용으로 `selectedStock` 상태를 별도로 유지한다.
- 신규 거래 모달을 열 때 `stockId`와 `selectedStock`을 비운다.
- 거래 수정 모달을 열 때 행의 `stockId`, `stockCode`, `stockName`, `marketCode`, `currencyCode`로 `selectedStock`을 구성하여 현재 종목을 표시한다.
- 저장 시 `form.stockId`가 없으면 `종목을 선택해 주세요.` 오류를 표시하고 API를 호출하지 않는다.

### 6.5 종목 선택 모달

- 돋보기 버튼을 누르면 거래 모달 위에 종목 선택 모달을 연다.
- 모달이 열릴 때 `GET /api/stocks`를 호출하여 활성 종목을 가져온다.
- 검색 조건은 종목코드 또는 종목명 키워드와 시장코드이며, 조회 버튼 또는 Enter로 API를 다시 호출한다.
- 결과는 PrimeVue `DataTable`로 표시하고 종목코드, 종목명, 시장코드, 통화코드를 보여 준다.
- 행 클릭 또는 행의 `선택` 버튼으로 한 건을 선택한다.
- 선택하면 `form.stockId`와 `selectedStock`을 갱신하고 선택 모달만 닫는다. 거래 폼의 다른 입력값은 유지한다.
- 닫기·취소 시 기존 선택값과 거래 폼 값을 변경하지 않는다.
- 조회 중, 조회 실패, 검색 결과 없음 상태를 구분해 표시한다.
- 사용 중지되거나 삭제된 종목은 기존 활성 종목 API에서 제외되므로 선택할 수 없다.
- 선택 직후 종목이 다른 요청에서 비활성화되더라도 저장 시 백엔드의 활성 종목 검증 오류를 표시한다.
- 거래 화면의 시장 필터와 기존 거래 목록 검색은 유지한다. 초기 활성 종목 조회로 구성하던 시장 목록도 현행 방식으로 유지하여 기존 필터 동작을 바꾸지 않는다.

### 6.6 스타일 및 접근성

- 새 관리 화면과 선택 모달은 `AccountSettingsView.vue`의 페이지 폭, 패널, 검색 행, DataTable, 모달, 버튼, 상태 배지, 알림과 반응형 기준을 재사용한다.
- 공통 스타일로 해결되지 않는 종목 선택 필드, 중첩 모달의 z-index, 선택 결과 행, 모바일 배치만 화면 scoped 스타일로 추가한다.
- 중첩 모달에서도 ESC/닫기/배경 클릭이 선택 모달과 거래 모달을 혼동하지 않도록 각각의 닫기 동작을 분리한다.
- 아이콘 전용 버튼에는 `title`과 `aria-label`을 제공하고 모든 비제출 버튼에는 `type="button"`을 명시한다.
- 좁은 화면에서는 DataTable 가로 스크롤을 허용하고, 등록·수정 폼과 검색 조건은 한 열로 배치한다.

## 7. DB 변경

DB 스키마와 데이터 변경은 없다.

- `stock_master` 테이블, 컬럼, 기본값, 기본키 및 `uq_stock_master` 제약조건을 그대로 사용한다.
- 신규 테이블, 컬럼, 인덱스, 시퀀스, 트리거 또는 마이그레이션 파일을 추가하지 않는다.
- `stock_trade` 및 기존 거래 데이터는 수정하거나 삭제하지 않는다.
- 애플리케이션 등록은 DB 기본값에만 의존하지 않고 `currencyCode`와 `useYn`의 누락값을 Service에서도 각각 `KRW`, `true`로 정규화한다.
- `updated_at`은 수정 및 사용 상태 변경 SQL에서 `CURRENT_TIMESTAMP`로 갱신한다.
- `market_code IS NULL` 중복은 현재 제약조건의 한계 때문에 애플리케이션에서 보완한다. 완전한 동시성 보장이 필요하면 향후 별도 승인 아래 `NULLS NOT DISTINCT` 유일 인덱스 등 DB 변경을 검토하되 이번 범위에는 포함하지 않는다.

## 8. 기존 동작 유지 조건

- 거래 목록 조회, 필터, 서버 페이징, 요약, 등록·수정·삭제 동작과 API URL은 유지한다.
- 거래 생성·수정 요청의 필드명과 `stockId` 기반 계약을 변경하지 않는다.
- 기존 거래 조회는 종목의 현재 `use_yn` 값과 관계없이 `stock_master`를 조인하여 과거 종목정보를 계속 표시한다.
- 신규 거래 및 변경된 거래에는 기존과 동일하게 사용 중인 증권사, 계좌, 종목만 허용한다.
- 증권사 선택에 따른 계좌 목록 갱신, 거래 금액 계산, 매수·매도 검증, 메모 처리와 오류 메시지 흐름은 유지한다.
- 거래 목록의 종목 키워드·시장 필터는 유지한다.
- `GET /api/stocks`의 기존 응답 필드와 활성 종목만 반환하는 조건은 유지한다.
- 증권사·계좌 관리 화면과 해당 API는 변경하지 않는다.
- 기존 공통 셸, 라우트, PrimeVue 설정, 전역 색상·버튼·DataTable 테마를 유지한다.
- 종목 관리 삭제는 거래 내역을 연쇄 삭제하지 않는다. 참조 종목은 삭제 거부하고 사용 중지로 관리한다.

## 9. 구현 제약

- 이 명세 작성 단계에서는 본 명세 파일 외 애플리케이션 소스와 DB를 수정하지 않는다.
- 구현 시에도 변경 범위를 이 명세의 파일과 직접 의존 코드로 제한하고 unrelated 리팩터링을 하지 않는다.
- 새 프레임워크, 상태 관리 방식 또는 npm/Gradle 의존성을 추가하지 않는다.
- 기존 Spring JDBC, record DTO, Service 트랜잭션, `ApiException`, fetch 기반 API 모듈, Vue Composition API 및 PrimeVue 패턴을 따른다.
- 종목 관리 기능을 기존 거래 Controller에 혼합하지 않고 `master` 패키지의 별도 종목 관리 계층으로 분리한다.
- 관리 API와 거래 선택 API의 목적을 분리한다. 관리 API는 활성·비활성 전체를 다루고, 거래 선택 API는 활성 종목만 제공한다.
- 종목 식별은 화면 문자열이 아니라 `stock_id`로 수행한다.
- 목록 전체를 프론트엔드에서 임의 페이징하지 않는다. 관리 목록은 서버 페이징을 사용한다.
- 직접 SQL 문자열 결합에 사용자 입력을 넣지 않고 모든 값은 named parameter로 바인딩한다.
- 종목 삭제 시 거래 데이터 삭제를 허용하지 않는다.
- 사용자 제공 `stock_master` DDL 외에 확인되지 않은 외래키나 인덱스를 존재한다고 가정하지 않는다.
- 현재 작업 트리에 이미 있는 미커밋 애플리케이션 변경은 보존하고 덮어쓰지 않는다.

## 10. 검증 방법

### 10.1 Backend 자동 검증

1. `backend`에서 `gradlew.bat compileJava`를 실행하여 신규 Controller, DTO, Service, Repository가 컴파일되는지 확인한다.
2. `StockMasterServiceTest`에서 다음을 검증한다.
   - 정상 등록과 기본값 `KRW`, `useYn=true`
   - 필수값, 공백값, 각 컬럼 최대 길이 초과의 HTTP 400
   - `marketCode` 빈 문자열의 `null` 변환
   - 동일 종목코드/시장 조합과 둘 다 null인 시장 조합 중복의 HTTP 409
   - 수정 시 자기 자신 제외 중복 검사
   - 없는 종목의 조회·수정·삭제 HTTP 404
   - 사용 상태 변경
   - 참조 거래가 있는 종목 삭제 HTTP 409 및 delete 미호출
   - 참조 거래가 없는 종목 삭제 성공
3. 기존 `StockTradeServiceTest`를 실행하여 활성 종목 검증과 거래 계산·저장 동작이 유지되는지 확인한다.

### 10.2 Frontend 정적 검증

1. `frontend`에서 `npm run type-check`를 실행하여 신규 API 타입, 라우트, DataTable 슬롯, 선택 모달 상태에 타입 오류가 없는지 확인한다.
2. `frontend`에서 `npm run build-only`를 실행하여 프로덕션 빌드가 완료되는지 확인한다.
3. 소스 검색으로 `StockTradeView.vue`의 `datalist`, `stockInput`, 직접 종목 입력 및 문자열 추론 함수가 제거되었는지 확인한다.
4. Git diff로 변경 범위가 명세의 변경 대상에 한정되고 DB/마이그레이션 파일이 추가되지 않았는지 확인한다.

### 10.3 API 수동 검증

1. 종목 관리 목록을 키워드, 시장코드, 사용 상태별로 조회하고 전체 건수와 페이지가 일치하는지 확인한다.
2. 모든 필드를 입력한 등록, `currencyCode`/`useYn`을 생략한 등록, 수정, 상태 변경, 미사용 종목 삭제의 상태 코드와 응답을 확인한다.
3. 동일 `(stockCode, marketCode)` 및 동일 `(stockCode, null)` 등록이 HTTP 409인지 확인한다.
4. 거래가 존재하는 종목 삭제가 HTTP 409이고 `stock_master`와 `stock_trade` 데이터가 모두 유지되는지 확인한다.
5. 사용 중지 종목이 관리 목록에는 표시되지만 `GET /api/stocks`에는 표시되지 않는지 확인한다.
6. 기존 거래 목록에서 사용 중지 종목의 종목명·종목코드가 계속 조회되는지 확인한다.

### 10.4 화면 수동 검증

1. 사이드바의 `주식종목 관리` 메뉴, 라우트 이동, 페이지 제목과 활성 메뉴 표시를 확인한다.
2. 종목 관리 화면의 검색·초기화·상태 필터·시장 필터·페이지 이동과 빈 결과·로딩·오류 상태를 확인한다.
3. 종목 등록·수정·사용 상태 변경·삭제 성공 후 목록과 전체 건수가 올바르게 갱신되는지 확인한다.
4. 참조 거래가 있는 종목 삭제 시 거래 삭제 없이 안내 메시지가 표시되는지 확인한다.
5. 거래 신규 모달에서 종목을 직접 입력할 수 없고 돋보기 버튼만으로 선택 모달이 열리는지 확인한다.
6. 선택 모달의 최초 조회, 키워드·시장 검색, 빈 결과, 오류, 행 선택 및 취소 동작을 확인한다.
7. 종목 선택 후 거래 폼에 표시 정보와 `stockId`가 설정되고 다른 거래 입력값은 유지되는지 확인한다.
8. 거래 수정 모달에서 기존 종목이 즉시 표시되고 돋보기로 다른 활성 종목을 선택할 수 있는지 확인한다.
9. 종목 미선택 저장 차단, 비활성화 경쟁 상황의 서버 오류 표시, 정상 거래 등록·수정을 확인한다.
10. 데스크톱과 모바일 폭에서 관리 DataTable, 거래 모달과 종목 선택 모달의 스크롤·버튼 접근성을 확인한다.

## 11. 확정 사항과 제한사항

- 물리 삭제는 거래에서 사용되지 않은 종목에만 허용한다. 거래가 있는 종목은 사용 중지로 관리한다.
- 거래 종목 선택은 기존 활성 종목 API를 재사용하고 관리 API와 분리한다.
- 관리 목록은 서버 페이징, 거래 선택 결과는 기존 API의 최대 200건 제한을 사용한다. 대규모 데이터에서 무검색 결과가 200건을 넘으면 키워드 또는 시장코드로 검색해야 한다.
- 제공된 `stock_master` DDL은 변경하지 않는다. `market_code IS NULL` 조합의 동시 등록까지 DB 수준에서 완전히 차단하는 것은 이번 범위 밖이며, 애플리케이션의 null-safe 사전 검사로 보완한다.
