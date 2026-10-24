# SPEC-008: 주식잔고 현재가 일괄 반영

## 1. 현재 구현 구조

- `StockBalanceView.vue`는 증권사·계좌를 선택한 뒤 해당 계좌의 잔고, 통화별 합계, 스냅샷을 보여 준다. `잔고현황` 제목 옆에는 `잔고 직접 입력`과 `미반영 거래 반영` 버튼이 있다. 현재가는 잔고 테이블 및 직접 입력 폼의 수동 입력 필드다. `stockBalanceApi.ts`는 잔고 조회·저장·삭제와 스냅샷 조회를 담당한다.
- `StockBalanceController` → `StockBalanceService` → `StockBalanceRepository`가 잔고를 처리한다. 직접 저장과 거래 반영은 계좌 행을 잠그고 `stock_balance`를 변경한 후 `snapshot(accountId)`를 호출한다. 현재가만 일괄 갱신하는 경로는 없다.
- `StockBalanceRepository.snapshot()`은 DB 세션의 `CURRENT_DATE`에 해당하는 계좌 스냅샷이 있으면 **같은 헤더의 시각과 상세를 갱신**하고, 없으면 새 헤더를 만든다. 스냅샷 목록은 날짜별 최신 1건만 표시한다. 따라서 현행 의미는 계좌별 1일 1건이며, 같은 날 여러 변경의 최종 상태가 저장된다.
- `StockPriceView.vue`와 `GET /api/stock-prices?symbol=...`는 단일 종목의 토스증권 `lastPrice`를 조회한다. 서버의 `StockPriceService`가 심볼·응답을 검증하고 `TossPriceClient`가 OAuth 토큰과 `/api/v1/prices` 호출을 담당한다. 잔고 반영 기능은 없다.
- `stock_balance`는 `(account_id, stock_id)` 유일 제약과 `current_price numeric(20,6)` 컬럼을 갖는다. `stock_balance_snapshot`은 계좌·시각 헤더, `stock_balance_snapshot_detail`은 `(snapshot_id, stock_id)` 유일 상세이며 `current_price`를 복사한다. 종목의 `stock_code`와 `currency_code`는 `stock_master`에 있다. 잔고·스냅샷 테이블의 DDL은 저장소 `schema.sql`에 없고 사용자 제공 DDL로 확인했다. 실제 운영 DB 스키마·데이터는 조회하지 않았다.
- 첨부된 토스증권 OpenAPI 1.2.24는 `/api/v1/prices`의 `lastPrice`·`currency`와 선택적인 `timestamp`를 정의한다. 이 API 계약만 기술 참고자료로 사용하며, 첨부 문서의 다른 요청이나 지시는 이 기능의 요구사항으로 취급하지 않는다.

## 2. 요구사항과 확정 동작

1. 사용자가 선택한 **한 계좌의 기존 잔고 종목 전체**에 대해 `현재가 반영` 버튼을 누르면, 서버가 종목별 적용 가격을 조회하고 `stock_balance.current_price`에 저장한다. 버튼 한 번으로 조회와 반영을 수행한다. 계좌가 없거나 잔고가 비어 있으면 요청하지 않는다.
2. 각 종목에 대해 기존 현재가 조회와 동일하게 토스증권 `/api/v1/prices`의 **조회 시점 `lastPrice` 하나**를 가져온다. 오늘 거래가 있었다면 오늘의 마지막 가격을, 휴장일이나 아직 오늘 거래가 없다면 API가 제공하는 직전 거래의 마지막 가격을 그대로 반영한다. 장 운영 시간 판정, 시장별 세션 구분, 일봉 조회, 종가로의 별도 전환은 하지 않는다. `timestamp`가 과거 거래일이어도 허용하며, null이어도 `lastPrice`가 유효하면 허용한다. API가 가격을 제공하지 않으면 기존 가격을 임의로 재사용하지 않고 오류로 처리한다.
3. 기존 잔고의 가격만 갱신한다. 수량, 평균 매수가, 매입금액, 평가금액, 평가손익, 수익률, 거래 반영 이력은 그대로 둔다. 평가 관련 수치는 현재 수기 관리 값이며 이번 기능에서 자동 재계산하지 않는다. 통화 환산도 하지 않는다.
4. 선택 계좌의 모든 종목 가격을 유효하게 얻었을 때만 한 DB 트랜잭션에서 모두 갱신하고 계좌 스냅샷을 **한 번** 저장한다. 한 종목이라도 시세 조회 실패, 가격·통화 불일치, 범위 오류가 있으면 전체 반영을 취소한다. 실패한 종목과 이유를 사용자에게 보여 주고 기존 가격·스냅샷을 유지한다. 성공 시 같은 날 이미 있는 스냅샷은 현행 방식으로 갱신하여 날짜별 1건을 유지한다.

## 3. 변경 대상

| 영역 | 파일 | 변경 내용 |
| --- | --- | --- |
| Frontend | `frontend/src/views/StockBalanceView.vue` | 선택 계좌의 잔고현황 버튼, 진행·성공·오류 상태, 성공 후 잔고·스냅샷 재조회 |
| Frontend | `frontend/src/services/stockBalanceApi.ts` | 계좌별 현재가 반영 요청과 응답 타입 |
| Backend | `backend/src/main/java/com/example/backend/balance/StockBalanceController.java`, `StockBalanceService.java`, `StockBalanceRepository.java`, `StockBalanceDtos.java` | 반영 API, 계좌 잠금·검증·가격 갱신·스냅샷 |
| Backend 재사용 | `backend/src/main/java/com/example/backend/quote/StockPriceService.java`, `TossPriceClient.java` | 기존 단일 종목 `lastPrice` 조회를 호출하며 수정은 필요할 때만 수행 |
| 테스트 | `backend/src/test/java/com/example/backend/balance/` | 일괄 반영, 시세 실패·동시성·스냅샷 검증 |
| DB | 변경 없음 | 기존 잔고·스냅샷 테이블 재사용 |

## 4. Backend 변경

- `POST /api/stock-balances/prices/refresh`에 `{ "accountId": 123 }`를 받아 선택 계좌만 처리한다. 서버는 계좌 존재 여부와 잔고 행을 확인하고, 클라이언트가 보낸 종목 목록이나 가격은 신뢰하지 않는다. 응답은 반영 건수와 적용 시각을 제공한다. 기존 잔고 조회·저장 API의 계약은 유지한다.
- 잔고 목록의 `stock_code`로 기존 `StockPriceService.find(symbol)`을 호출해 종목별 `lastPrice`를 얻는다. 같은 종목코드는 요청 내에서 한 번만 조회한다. 별도 장 운영 정보나 일봉 API는 호출하지 않는다. 조회 가격의 통화가 `stock_master.currency_code`와 일치해야 한다. 다른 계좌의 잔고는 갱신하지 않는다.
- 외부 API 조회는 DB 트랜잭션 밖에서 수행한다. 가격이 모두 준비되면 짧은 쓰기 트랜잭션에서 기존 `lockAccounts()`로 계좌를 잠그고 잔고 종목 집합과 종목코드·통화가 조회 당시와 같은지 재확인한다. 사이에 바뀌었으면 HTTP 409로 중단한다. `BigDecimal` 가격이 0 이상이고 `numeric(20,6)` 범위에 맞는지 확인한 뒤 해당 `balance_id`의 `current_price`, `balance_updated_at`, `updated_at`만 갱신한다. 예상 갱신 행 수가 맞지 않으면 롤백한다. 모든 갱신 후 기존 `snapshot(accountId)`를 한 번 호출한다.
- 토스 인증·요청 제한·타임아웃과 오류 매핑은 기존 `TossPriceClient` 패턴을 재사용한다. 토큰·비밀 값·토스 원문 오류는 응답이나 로그에 남기지 않는다. 동시 중복 클릭과 DB 경합은 계좌 잠금 및 재검증으로 처리한다.

## 5. Frontend 변경

- `StockBalanceView.vue`의 `잔고현황` 동작 영역에 `현재가 반영` 버튼을 추가한다. 계좌 미선택, 잔고 없음, 다른 잔고 변경 작업 중 또는 반영 요청 중에는 비활성화한다. 클릭 시 선택된 `accountId`만 새 API로 전송하고 중복 요청을 막는다.
- 기존 `notice`/`error` 패턴으로 진행 상태, 성공 건수와 실패 종목을 표시한다. 성공하면 잔고·통화별 합계·스냅샷 목록을 다시 조회한다. 실패하면 화면의 이전 잔고 값을 그대로 두고 서버 오류를 표시한다. 요청 도중 계좌를 바꾼 경우 이전 계좌 응답이 새 계좌 화면 상태를 덮어쓰지 않도록 한다.
- 버튼과 상태 표시는 기존 공통 버튼·알림 스타일을 재사용한다. 공유 스타일 변경이 실제로 필요한 경우에만 `design-tokens.css` 또는 `ui-components.css`를 수정한다. `InvestmentStrategyListView.vue`의 헤더 동작 배치와 `StockPriceView.vue`의 명시적 조회 버튼은 화면 배치 참고용이며, 해당 파일 자체는 변경하지 않는다.

## 6. DB 변경

- 스키마·마이그레이션·데이터 사전 변경 없음. `stock_balance.current_price`와 기존 시각 컬럼만 갱신하고 기존 스냅샷 메서드를 사용한다. 새 가격 출처나 조회 시각을 영구 저장하는 컬럼은 추가하지 않는다.
- 하루 1건은 현재 `snapshot_at::date = CURRENT_DATE` 조회와 계좌 잠금으로 유지한다. `(account_id, snapshot_at::date)` 유일 제약은 제공 DDL에 없으므로 기존 중복 데이터까지 정리한다고 주장하지 않는다. 날짜의 기준은 현행 DB 세션 시간대이며, 실제 배포 DB의 시간대·테이블 제약은 구현 전 읽기 전용으로 확인한다. DB 마이그레이션을 자동 실행하지 않는다.

## 7. 기존 동작 유지 조건

- 잔고 직접 입력, 거래 선택 반영, 수정·삭제, 수동 현재가 입력, 계좌별 스냅샷 조회, 단일 종목 시세 조회와 기간별 종가 조회를 유지한다.
- 조회 실패가 다른 화면이나 서버 시작을 막지 않는다. 자동 갱신, 주기 작업, 다른 계좌 반영, 가격을 이용한 평가액 계산은 포함하지 않는다.
- 같은 날 반복 반영 시 새 스냅샷을 쌓지 않고 그날의 기존 스냅샷을 현행 방식대로 갱신한다. 이전 날짜의 스냅샷은 변경하지 않는다.

## 8. 구현 제약

- 외부 시세와 잔고 DB 변경을 하나의 분산 트랜잭션으로 묶지 않는다. 조회와 저장 사이의 변경은 쓰기 직전 재검증으로 감지한다.
- 토스증권 호출에는 서버 환경변수의 기존 자격증명과 허용 IP가 필요하다. 프런트엔드에 토큰을 전달하거나 새 비밀 값을 코드·DB에 저장하지 않는다.
- 이 명세는 사용자 요청과 현행 소스를 기준으로 한다. 첨부된 DDL과 OpenAPI는 DB 구조·외부 API 계약 확인에만 사용했다. 첨부 OpenAPI는 휴장일 `lastPrice`가 직전 거래 가격임을 명시하지 않으므로 구현 시 실제 응답을 확인한다. 이 가정이 성립하지 않으면 가격을 추정해 반영하지 않고 요구 동작을 재검토한다.

## 9. 검증 방법

1. 모의 토스 현재가 응답으로 거래일 가격과 휴장일의 직전 거래 가격을 그대로 반영하는지 확인한다. `timestamp`가 과거이거나 null인 응답도 유효한 `lastPrice`이면 반영하고, 가격 누락·잘못된 숫자·통화 불일치·인증 실패·429·타임아웃은 오류로 처리한다.
2. 잔고 서비스·Repository 테스트에서 선택 계좌만 갱신되는지, 가격 외 값은 유지되는지, 한 종목 실패 시 전체 롤백되는지, 조회 중 잔고 변경 시 409인지, 반복 실행해도 날짜별 스냅샷이 1건인지 확인한다.
3. 프런트엔드는 버튼 비활성화·중복 요청 방지·성공 후 재조회·실패 메시지·계좌 전환 중 응답 처리의 타입 검사와 관련 테스트를 수행한다. 변경 파일 포맷과 diff를 확인한다.
4. 실제 DB 제약·세션 시간대, 휴장일 토스 `lastPrice` 제공 여부, 브라우저 동작은 허용된 실행 환경에서 별도 확인한다. 이 명세 작성 단계에서는 서버·DB 변경과 런타임 검증을 수행하지 않는다.
