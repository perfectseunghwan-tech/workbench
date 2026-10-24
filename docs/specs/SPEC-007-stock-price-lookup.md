# SPEC-007: 토스증권 종목 현재가 조회

## 1. 현재 구현 구조

- Backend는 Spring Boot 3.2.4, Java 21 기반이며 Controller → Service → Repository 구조로 PostgreSQL에 접근한다. 외부 HTTP 호출에는 기존 채팅 기능이 Spring `RestClient`를 사용한다.
- `StockMasterController`의 `/api/master/stocks`는 `stock_master`의 종목코드, 종목명, 시장코드, 통화코드, 사용 여부를 관리한다. `StockTradeController`의 `/api/stocks`는 사용 중인 종목 선택 목록을 제공한다. 두 API 모두 시세 조회 기능은 없다.
- 공통 `ApiExceptionHandler`는 `ApiException`을 HTTP 상태와 `message`를 포함하는 JSON으로 변환한다.
- Frontend는 Vue 3·TypeScript·Vue Router 기반이다. `AppShell.vue`가 사이드바를, `router/index.ts`가 화면 경로를 정의한다. 서비스 모듈은 같은 출처의 `/api/...`에 `fetch`로 요청한다. `vite.config.ts`는 개발 환경의 `/api` 요청을 Backend로 프록시한다.
- `StockSettingsView.vue`는 등록 종목 관리 화면이고, `StockBalanceView.vue`의 현재가는 잔고 데이터의 수동 입력 값이다. 현재가 전용 조회 화면과 토스증권 연동은 없다.
- 저장소의 `backend/src/main/resources/schema.sql`에는 종목 마스터나 잔고 테이블 생성 DDL이 없으며, 관련 Repository SQL과 기존 명세에서 `stock_master.stock_code`, `stock_master.market_code`, `stock_master.currency_code`, `stock_balance.current_price` 사용을 확인했다. 실제 운영 DB 접속·실데이터 검사는 수행하지 않았다.
- 첨부된 토스증권 OpenAPI 3.1.0 문서 버전 1.2.24는 `POST /oauth2/token`과 `GET https://openapi.tossinvest.com/api/v1/prices?symbols=...`를 정의한다. 현재가 응답은 `result` 배열이며 각 항목에 `symbol`, `lastPrice`(decimal 문자열), `currency`, 선택적인 `timestamp`(null 가능)가 있다.

## 2. 요구사항

1. 사용자가 화면에 종목코드 또는 심볼 하나를 입력하고 조회하면 해당 종목의 현재가, 통화, 데이터 시각을 표시한다. 예: 국내 `005930`, 해외 `AAPL`.
2. 조회 시 Backend가 토스증권 API를 호출한다. 브라우저는 토스증권 자격증명이나 액세스 토큰을 받지 않는다.
3. 입력 전, 조회 중, 결과 없음, 입력 오류, 외부 API 오류를 화면에서 구분해 표시한다. `timestamp`가 null이면 시각을 `정보 없음`으로 표시한다.
4. 조회는 명시적 버튼 또는 Enter 입력으로 실행한다. 이번 범위는 **단일 종목의 일회성 현재가 조회**이며 자동 갱신, 실시간 WebSocket, 종목 검색·자동완성, 잔고 현재가 자동 반영은 포함하지 않는다.

## 3. 변경 대상

| 영역 | 파일 | 계획 |
| --- | --- | --- |
| Backend 신규 | `backend/src/main/java/com/example/backend/quote/StockPriceController.java` | 내부 조회 API |
| Backend 신규 | `backend/src/main/java/com/example/backend/quote/StockPriceService.java` | 입력 검증, 토큰 사용 및 응답 정리 |
| Backend 신규 | `backend/src/main/java/com/example/backend/quote/TossPriceClient.java` | OAuth2 및 토스증권 HTTP 호출 |
| Backend 신규 | `backend/src/main/java/com/example/backend/quote/StockPriceDtos.java` | 내부 API 응답과 외부 응답 매핑 DTO |
| Backend 신규 | `backend/src/main/java/com/example/backend/quote/TossPriceConfiguration.java` | 전용 `RestClient`와 연결·읽기 시간 제한 |
| Backend 기존 | `backend/src/main/resources/application.properties` | 토스 API 기본 URL과 환경변수 참조 설정만 추가 |
| Backend 테스트 신규 | `backend/src/test/java/com/example/backend/quote/StockPriceServiceTest.java`, `TossPriceClientTest.java` | 검증·응답·인증·오류 테스트 |
| Frontend 신규 | `frontend/src/services/stockPriceApi.ts` | 내부 조회 API 호출 및 타입 |
| Frontend 신규 | `frontend/src/views/StockPriceView.vue` | 코드 입력 및 현재가 결과 화면 |
| Frontend 기존 | `frontend/src/router/index.ts`, `frontend/src/views/AppShell.vue` | `/prices` 경로와 사이드바 항목 |
| Frontend 스타일 | `frontend/src/assets/design-tokens.css`, `frontend/src/assets/ui-components.css` | 새 공유 시각 요소가 필요한 경우에만 보완 |
| DB | 변경 없음 | 조회 결과를 저장하지 않음 |

기존 `StockSettingsView.vue`와 `StockBalanceView.vue`에는 이미 사용자 변경 사항이 있으므로 이 기능 구현에서 수정하지 않는다.

## 4. Backend 변경

### 4.1 내부 API 계약

- `GET /api/stock-prices?symbol={code}`를 추가한다. 입력은 앞뒤 공백을 제거하고, 빈 값·쉼표를 포함한 다건 입력·`[A-Za-z0-9.\-]` 이외의 문자는 HTTP 400으로 거절한다. 선행 0을 보존하고 URL 쿼리는 HTTP 클라이언트가 인코딩한다.
- 성공 응답 예: `{ "symbol": "005930", "lastPrice": "72000", "currency": "KRW", "timestamp": "2026-03-25T09:30:00.123+09:00" }`. `timestamp`는 null일 수 있다. `lastPrice`는 손실 없는 decimal 문자열로 전달한다. 종목명, 등락률 등 첨부 스펙에 없는 필드는 만들지 않는다.
- 토스의 `result` 배열에서 요청 심볼과 일치하는 항목을 선택한다. 빈 배열 또는 일치 항목 없음은 HTTP 404로 처리한다. 성공 상태라도 필수 필드가 없거나 가격 형식이 잘못되면 HTTP 502로 처리한다.
- 내부 오류 응답은 기존 `ApiExceptionHandler`의 `message` 형식을 재사용한다. 잘못된 입력 400, 종목 없음 404, 토스 요청 제한 429, 자격증명 미설정·인증 실패·토스 서비스 오류 503, 응답 형식 오류 502, 연결 시간 초과 504로 매핑한다. 토스 원문 오류, 토큰, 비밀 값은 사용자 응답과 로그에 포함하지 않는다.

### 4.2 토스증권 연동

- 서버에서만 `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET` 환경변수를 읽는다. 기본 URL은 `https://openapi.tossinvest.com`으로 설정한다. 비밀 값은 소스, 프론트 번들, DB에 기록하지 않는다. 설정이 없을 때 기존 애플리케이션의 다른 기능은 시작·이용 가능하게 두고 조회 요청에만 503을 반환한다.
- `POST /oauth2/token`에 `application/x-www-form-urlencoded`의 `grant_type=client_credentials`, `client_id`, `client_secret`을 보내고, 응답의 `access_token`·`expires_in`을 사용한다. 토큰 응답은 시세 응답의 `result` envelope와 다르므로 별도로 파싱한다.
- 서버 메모리에 토큰과 만료 시각을 보관하고 만료 전 여유 시간을 두고 재사용한다. 동시 조회에서 토큰 재발급은 직렬화한다. 토스 문서상 클라이언트당 유효 토큰이 1개이므로 매 조회마다 재발급하지 않는다. 인증 실패 시 캐시를 무효화하고 필요한 경우 **한 번만** 재발급·재시도한다.
- `GET /api/v1/prices`에 단일 `symbols` 쿼리와 `Authorization: Bearer {access_token}` 헤더를 전달한다. 토스의 200 응답은 `result` 배열이고, 400·404·429·500 등 오류는 `error` envelope다. 429에 대한 무제한 자동 재시도는 하지 않는다. 연결·읽기 시간 제한을 설정한다.
- 기존 `openAiRestClient` Bean과 구분되는 전용 HTTP 클라이언트를 사용한다. 외부 API 호출에는 DB 트랜잭션을 걸지 않는다.

## 5. Frontend 변경

- `/prices` 화면과 `현재가 조회` 메뉴를 추가한다. 화면에 종목코드 입력, 조회 버튼, 조회 중 상태, 조회된 심볼·현재가·통화·데이터 시각을 배치한다. 시각은 응답의 시간대 정보를 반영해 사용자 로컬 시간으로 표시하고, null이면 `정보 없음`을 표시한다.
- `stockPriceApi.ts`는 `URLSearchParams`로 `symbol`을 전달하고 내부 API의 응답 타입을 정의한다. 실패 시 기존 서비스 패턴처럼 `message`를 읽어 화면에 보여 준다.
- 빈 값이나 허용되지 않는 문자는 화면에서도 즉시 안내하고 요청하지 않는다. 서버 검증이 최종 기준이다. 조회 중 중복 요청을 막고, 새 조회 시작 시 이전 결과를 지워 다른 종목의 오래된 가격이 남지 않게 한다. 조회 성공 시 가격 문자열을 숫자 연산 없이 표시 형식만 조정한다.
- 기존 디자인 토큰과 공통 UI 스타일을 사용한다. 공유 색상·입력·버튼·알림 형태가 새로 필요할 때만 `design-tokens.css`와 `ui-components.css`에 정의하고, 화면 자체의 배치만 뷰에 둔다.

## 6. DB 변경

- 스키마, 데이터, 마이그레이션 변경 없음. 이 화면은 종목코드를 토스증권에 직접 조회하며 `stock_master` 등록 여부와 무관하다.
- `stock_balance.current_price`와 평가 관련 필드를 읽거나 갱신하지 않는다. 조회 결과, 토큰, 오류 내역을 DB에 저장하지 않는다.

## 7. 기존 동작 유지 조건

- 종목 관리, 거래, 잔고, 투자전략, 투자일지, AI 기능의 API·화면·저장 동작을 바꾸지 않는다.
- 시세 조회 실패 또는 토스 인증 정보 미설정이 다른 화면이나 애플리케이션 시작을 막지 않는다.
- 잔고의 수동 현재가와 토스 조회 현재가를 자동 동기화하지 않는다. 조회 값이 실시간 체결가라는 표현도 사용하지 않는다.

## 8. 구현 제약

- 첨부 OpenAPI 버전 1.2.24를 계약 기준으로 한다. `GET /api/v1/prices`는 최대 200개 심볼을 지원하지만 이 기능은 1개만 전달한다. 심볼 허용 문자 규칙은 첨부 스펙을 따른다.
- 환경변수의 실제 값 생성·변경은 구현 범위에서 제외한다. 외부 API 사용에 필요한 계정, 자격증명, 호출 허용 IP와 네트워크 접근은 배포 환경에서 제공되어야 한다.
- 새 의존성, DB 테이블, 마이그레이션, 개발 서버 자동 시작을 추가하지 않는다. 소스 수정 시 기존 구조와 포맷 규칙을 따른다.

## 9. 검증 방법

1. Backend 단위 테스트: 빈 값·허용되지 않는 심볼 400, 국내 선행 0 보존, 성공 응답과 nullable 시각, 빈 결과 404, 필수 필드 누락 502를 확인한다.
2. 모의 HTTP 응답을 이용한 연동 테스트: form-urlencoded 토큰 요청, Bearer 헤더와 `symbols` 전달, 토큰 재사용·동시 재발급 억제, 인증 실패 후 1회 재발급, 토스 429·500 및 연결 시간 초과 매핑을 확인한다. 실제 자격증명과 토스 서비스에 의존하지 않는다.
3. 변경한 Backend 테스트만 실행하고, Frontend는 타입 검사 및 관련 빌드를 실행한다. 화면에서 입력·로딩·결과·오류 표시를 코드 및 빌드 결과로 점검한다.
4. 실제 토스 API 및 브라우저 런타임 검증은 허용 IP·자격증명과 실행 환경이 제공되고 별도로 실행을 요청받은 경우 수행한다. 이 단계에서 수행하지 못하면 미검증으로 보고한다.
