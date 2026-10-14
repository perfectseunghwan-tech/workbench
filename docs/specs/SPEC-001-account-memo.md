# SPEC-001: 계좌 메모 이력 기능 전환

## 1. 목적

계좌의 단일 메모 속성을 계좌별 메모 이력으로 전환한다. 사용자는 각 투자 계좌에 메모를 추가하고, 작성 시각을 기준으로 메모 이력을 조회할 수 있어야 한다.

## 2. 범위

### 포함

- 계좌 기본 API에서 제거된 `investment_account.account_memo` 참조를 제거한다.
- 계좌 메모 이력의 조회 및 신규 등록 API를 제공한다.
- 계좌 관리 화면에서 계좌별 메모 이력을 조회하고 새 메모를 등록할 수 있게 한다.
- 계좌 목록에는 최신 메모를 표시한다.
- 변경 영역에 대한 서버 및 프론트엔드 검증을 수행한다.

### 제외

- 데이터베이스 마이그레이션, 기존 데이터 이관, 테이블 생성 및 기존 컬럼 삭제는 이미 완료되었으므로 본 작업 범위에서 제외한다.
- 기존 메모의 수정 및 삭제 기능은 제공하지 않는다.
- 거래 메모(`trade_memo`) 기능은 변경하지 않는다.
- 계좌·증권사 관리 기능의 그 밖의 동작과 UI 재설계는 하지 않는다.

## 3. 전제 조건

다음 데이터베이스 구조가 적용되어 있다.

- `investment_account.account_memo` 컬럼은 제거되었다.
- `investment_account_memo` 테이블이 존재한다.
- `investment_account_memo`의 주요 컬럼은 `memo_id`, `account_id`, `memo_content`, `created_at`이다.

애플리케이션은 더 이상 `investment_account.account_memo`를 조회, 등록 또는 수정해서는 안 된다.

## 4. 현재 구조와 변경 원칙

현재 계좌 관리 기능은 `InvestmentMasterController` → `InvestmentMasterService` → `InvestmentMasterRepository` 구조를 사용하며, 계좌 목록·상세·생성·수정이 모두 `AccountRequest` 및 `AccountResponse`의 `accountMemo`를 사용한다.

변경 후 계좌의 기본 정보와 메모 이력은 별도 리소스로 관리한다.

- 계좌 생성 및 수정 요청에서는 메모를 받지 않는다.
- 메모는 생성만 가능하며, 추가 시 기존 메모를 덮어쓰지 않는다.
- 메모 이력의 기본 정렬은 `created_at` 내림차순이며, 동일 시각에는 `memo_id` 내림차순을 보조 정렬로 사용한다.
- 계좌 목록의 메모 표시는 최신 메모 한 건만 사용한다.
- 최신 메모가 없으면 `null`을 반환하고 화면에서는 `—`로 표시한다.

## 5. Backend 변경 명세

### 5.1 계좌 DTO 및 기존 API 계약

대상 파일: `backend/src/main/java/com/example/backend/master/InvestmentMasterDtos.java`

- `AccountRequest`에서 `accountMemo`를 제거한다.
- `AccountResponse`에서 `accountMemo`를 제거한다.
- 계좌 목록의 최신 메모 표시에 필요한 읽기 전용 필드를 `AccountResponse`에 추가한다.
  - `latestMemoContent`: 최신 메모 내용, 없으면 `null`
  - `latestMemoCreatedAt`: 최신 메모 작성 시각, 없으면 `null`
- 계좌 상세 응답도 같은 최신 메모 필드를 제공하여 목록·상세 계약을 일관되게 유지한다.

### 5.2 메모 DTO

동일 DTO 파일 또는 현재 패키지의 응집도에 맞는 별도 DTO 파일에 아래 계약을 추가한다.

- `AccountMemoRequest`
  - `memoContent: String`
- `AccountMemoResponse`
  - `memoId: Long`
  - `accountId: Long`
  - `memoContent: String`
  - `createdAt: LocalDateTime`

### 5.3 Repository

대상 파일: `backend/src/main/java/com/example/backend/master/InvestmentMasterRepository.java`

- `ACCOUNT_SELECT`에서 `a.account_memo`를 제거한다.
- 계좌 목록과 상세 조회 SQL에 최신 메모를 조인한다.
  - 계좌별 최신 1건을 선택해야 하며, 계좌 목록의 페이징·총 건수에 중복 행이 생기지 않아야 한다.
  - PostgreSQL `LEFT JOIN LATERAL` 또는 동등한 단일 행 서브쿼리 방식을 사용한다.
- 계좌 INSERT SQL에서 `account_memo` 컬럼과 `:accountMemo` 파라미터를 제거한다.
- 계좌 UPDATE SQL에서 `account_memo` 갱신을 제거한다.
- 계좌 매퍼와 파라미터 생성 로직에서 기존 `accountMemo` 처리를 제거하고 최신 메모 필드를 매핑한다.
- 아래 메모 전용 데이터 접근 메서드를 추가한다.
  - 특정 계좌의 메모 목록 조회: `created_at DESC, memo_id DESC` 정렬
  - 메모 등록 후 생성된 `memo_id` 반환
  - 필요 시 메모 등록 전 계좌 존재 여부를 보장할 수 있는 조회

메모 목록은 초기 구현에서 비페이징으로 제공한다. 향후 메모량 증가가 확인되면 별도 요구 사항으로 페이징을 추가한다.

### 5.4 Service

대상 파일: `backend/src/main/java/com/example/backend/master/InvestmentMasterService.java`

- `normalizeAccount`에서 `accountMemo` 정규화 및 반환을 제거한다.
- `findAccountMemos(accountId)`를 추가한다.
  - 존재하지 않는 계좌는 기존 계좌 조회와 동일하게 404로 처리한다.
- `createAccountMemo(accountId, request)`를 추가한다.
  - 대상 계좌의 존재 여부를 확인한다.
  - 요청 본문과 `memoContent`는 필수이다.
  - 앞뒤 공백을 제거한 결과가 빈 문자열이면 400으로 처리한다.
  - 저장값은 정규화된 문자열을 사용한다.
  - 메모 길이 제한은 현 테이블이 `text` 타입이므로 별도 사업 규칙이 확정되기 전까지 추가하지 않는다.
- 메모 등록은 계좌 기본정보의 `updated_at`을 변경하지 않는다.

### 5.5 Controller

대상 파일: `backend/src/main/java/com/example/backend/master/InvestmentMasterController.java`

아래 엔드포인트를 추가한다.

| HTTP | 경로 | 요청 | 응답 | 설명 |
|---|---|---|---|---|
| GET | `/api/master/investment-accounts/{accountId}/memos` | 없음 | `AccountMemoResponse[]` | 계좌의 메모 이력 조회 |
| POST | `/api/master/investment-accounts/{accountId}/memos` | `AccountMemoRequest` | `201 Created`, `AccountMemoResponse` | 계좌에 새 메모 추가 |

- POST 성공 응답에는 `/api/master/investment-accounts/{accountId}/memos/{memoId}` 위치 헤더를 설정한다.
- 메모 수정·삭제 엔드포인트는 추가하지 않는다.

## 6. Frontend 변경 명세

### 6.1 API 타입 및 클라이언트

대상 파일: `frontend/src/services/investmentMasterApi.ts`

- `ManagedAccount`에서 `accountMemo`를 제거하고 `latestMemoContent`, `latestMemoCreatedAt`를 추가한다.
- `AccountPayload`에서 `accountMemo`를 제거한다.
- `AccountMemo`, `AccountMemoPayload` 타입을 추가한다.
- 아래 API 클라이언트 메서드를 추가한다.
  - `getAccountMemos(accountId)`
  - `createAccountMemo(accountId, payload)`

### 6.2 계좌 관리 화면

대상 파일: `frontend/src/views/AccountSettingsView.vue`

- 계좌 등록 및 수정 폼의 `accountMemo` 상태, textarea, payload 생성을 제거한다.
- 계좌 목록의 `메모` 열은 `latestMemoContent`를 표시한다.
  - 값이 없으면 `—`를 표시한다.
  - 기존 말줄임 스타일을 유지한다.
- 각 계좌 행에 메모 이력을 열 수 있는 동작을 추가한다. 기존 수정·상태 변경 동작을 훼손하지 않아야 한다.
- 메모 이력 UI는 선택한 계좌의 별도 모달로 제공한다.
  - 계좌 별칭을 제목 또는 보조 정보로 표시한다.
  - 최신순 메모 목록을 표시한다.
  - 각 항목에 메모 내용과 작성 시각을 표시한다.
  - 새 메모 입력 textarea와 등록 버튼을 제공한다.
  - 등록 성공 후 이력 목록과 계좌 목록을 다시 조회해 최신 메모를 즉시 반영한다.
  - 메모가 없을 때의 빈 상태와 요청 실패 메시지를 제공한다.
- 메모 등록 중에는 중복 요청을 막기 위해 등록 버튼을 비활성화한다.

## 7. 사용자 흐름

1. 사용자가 증권사·계좌 관리 화면에서 계좌 목록을 조회한다.
2. 각 행은 계좌의 최신 메모 한 건 또는 `—`를 표시한다.
3. 사용자가 특정 계좌의 메모 이력 열기 동작을 선택한다.
4. 화면은 해당 계좌의 메모를 최신순으로 조회해 모달에 표시한다.
5. 사용자가 유효한 메모를 입력하고 등록한다.
6. 서버는 새 행을 추가하고 생성 결과를 반환한다. 기존 메모는 변경되지 않는다.
7. 화면은 이력과 계좌 목록을 갱신해 새 메모를 최상단 및 최신 메모 열에 반영한다.

## 8. 오류 처리

- 존재하지 않는 계좌의 메모 조회·등록: 404, 기존 계좌 미존재 메시지 정책을 사용한다.
- `memoContent` 누락 또는 공백만 입력: 400, 사용자에게 메모 입력이 필요함을 알린다.
- API 호출 실패: 기존 `request` 공통 오류 처리와 화면의 `errorMessage` 패턴을 사용한다.
- 조회 중·등록 중 상태는 UI에서 명확히 구분한다.

## 9. 검증 계획

### Backend

- 계좌 목록, 상세, 생성, 수정, 상태 변경이 제거된 `account_memo` 컬럼 없이 동작하는지 확인한다.
- 계좌 목록 및 상세에서 최신 메모가 없을 때 `null`, 있을 때 최신 한 건과 시각이 반환되는지 확인한다.
- 동일 계좌에 여러 메모 등록 시 기존 메모가 보존되고 최신순으로 조회되는지 확인한다.
- 공백 메모와 존재하지 않는 계좌에 대한 요청이 적절한 400/404 응답을 반환하는지 확인한다.

### Frontend

- 계좌 등록 및 수정 요청에 `accountMemo`가 포함되지 않는지 확인한다.
- 계좌 목록이 최신 메모 및 빈 값 표기를 올바르게 표시하는지 확인한다.
- 메모 모달의 조회, 등록, 로딩, 빈 상태, 오류 상태를 확인한다.
- 메모 등록 뒤 목록의 최신 메모가 갱신되는지 확인한다.

## 10. 변경 대상 파일

- `backend/src/main/java/com/example/backend/master/InvestmentMasterDtos.java`
- `backend/src/main/java/com/example/backend/master/InvestmentMasterRepository.java`
- `backend/src/main/java/com/example/backend/master/InvestmentMasterService.java`
- `backend/src/main/java/com/example/backend/master/InvestmentMasterController.java`
- `frontend/src/services/investmentMasterApi.ts`
- `frontend/src/views/AccountSettingsView.vue`
- 관련 백엔드·프론트엔드 테스트 파일(현재 테스트 구성에 맞춰 추가 또는 수정)

## 11. 명시적 비변경 사항

- 데이터베이스 스키마와 데이터 이관은 변경하지 않는다.
- `trade_memo` 및 거래 관리 화면은 변경하지 않는다.
- 메모 수정·삭제·검색·페이징은 구현하지 않는다.
