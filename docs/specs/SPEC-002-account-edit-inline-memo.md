# SPEC-002: 계좌 수정 팝업 내 메모 이력 통합

## 1. 목적

계좌 기본정보 수정 팝업과 메모 이력/등록 팝업이 분리되어 있어 발생하는 화면 전환을 제거한다. 기존 계좌의 메모 등록 및 이력 조회 영역을 **계좌 수정 팝업 하단**에 함께 표시한다.

본 명세는 화면 배치 변경만 대상으로 한다. 계좌 메모는 계속 이력형 데이터로 관리하며, 기존 메모를 직접 수정하거나 삭제하는 기능은 추가하지 않는다.

## 2. 현재 구현 구조

### Frontend

- 대상 화면은 `frontend/src/views/AccountSettingsView.vue`이다.
- 계좌 행에는 메모 이력 버튼과 수정 버튼이 각각 있다.
  - 메모 이력 버튼은 `openMemoHistory(item)`을 호출하고, `showMemoHistory` 상태로 독립된 `memo-modal`을 연다.
  - 수정 버튼은 `openAccountEdit(item)`을 호출하고, `showAccountForm` 상태로 독립된 `account-modal`을 연다.
- 메모 모달은 새 메모 입력, 등록 버튼, 최신순 메모 이력을 제공한다.
- 계좌 수정 모달은 증권사, 계좌 별칭, 계좌번호, 사용 여부와 계좌 저장 버튼만 제공한다.
- `saveAccountMemo()`는 메모 등록 성공 뒤 메모 이력과 계좌 목록을 다시 조회한다. `saveAccount()`는 계좌 저장 성공 뒤 계좌 목록과 증권사 목록을 다시 조회하고 모달을 닫는다.
- API 클라이언트 `frontend/src/services/investmentMasterApi.ts`는 계좌 기본정보 API와 메모 이력 API를 이미 분리해 제공한다.

### Backend

- `InvestmentMasterController` → `InvestmentMasterService` → `InvestmentMasterRepository` 계층으로 구현되어 있다.
- 계좌 기본정보는 `PUT /api/master/investment-accounts/{accountId}`로 수정한다.
- 메모는 다음 별도 리소스로 제공한다.

| Method | Path | 동작 |
| --- | --- | --- |
| GET | `/api/master/investment-accounts/{accountId}/memos` | 계좌 메모 이력 조회 |
| POST | `/api/master/investment-accounts/{accountId}/memos` | 새 메모 이력 추가 |

- 메모 조회 정렬은 `created_at DESC, memo_id DESC`이며, 등록은 기존 행을 갱신하지 않고 새 행을 추가한다.
- 계좌 목록/상세 응답은 최신 메모(`latestMemoContent`, `latestMemoCreatedAt`)를 읽기 전용 정보로 포함한다.

### DB 구조

- 계좌 기본정보는 `investment_account` 테이블에 저장된다.
- 계좌 메모는 `investment_account_memo` 테이블에 별도 저장된다.
- 메모 테이블의 사용 컬럼은 `memo_id`, `account_id`, `memo_content`, `created_at`이다.
- `investment_account.account_memo` 컬럼은 사용하지 않으며, 계좌 기본정보 수정 SQL도 메모를 갱신하지 않는다.

## 3. 요구사항

1. 기존 계좌의 **계좌 수정** 팝업 하단에 메모 입력 및 메모 이력 영역을 연속해서 표시한다.
2. 수정 팝업을 열 때 해당 계좌의 최신 메모 이력을 조회한다.
3. 메모 입력 및 등록은 계좌 기본정보 저장과 독립적으로 동작한다. 메모 등록은 새 이력을 추가하며 기존 이력을 덮어쓰지 않는다.
4. 메모 등록이 성공하면 팝업 내부 이력과 계좌 목록의 최신 메모 표시를 즉시 갱신한다.
5. 메모 이력 전용 팝업은 제거하고, 계좌 목록에서 별도 메모 팝업을 여는 흐름은 제공하지 않는다.
6. 신규 계좌 등록은 아직 계좌 ID가 없으므로 기존 등록 팝업에는 메모 입력/이력 영역을 표시하지 않는다. 계좌 생성 후 수정 팝업을 다시 열어 메모를 추가한다.

## 4. 변경 대상

| 구분 | 파일 | 변경 내용 |
| --- | --- | --- |
| Frontend 화면 | `frontend/src/views/AccountSettingsView.vue` | 별도 메모 모달 제거, 수정 모달 하단에 메모 등록·이력 영역 배치, 상태 및 이벤트 흐름 통합 |
| Frontend API | `frontend/src/services/investmentMasterApi.ts` | 변경 없음. 기존 메모 조회/등록 메서드를 그대로 사용 |
| Backend | `InvestmentMasterController`, `InvestmentMasterService`, `InvestmentMasterRepository`, `InvestmentMasterDtos` | 변경 없음 |
| DB | `investment_account`, `investment_account_memo` | 변경 없음 |

## 5. Backend 변경

백엔드 변경은 없다.

- 기존 `GET /api/master/investment-accounts/{accountId}/memos`와 `POST /api/master/investment-accounts/{accountId}/memos`를 그대로 사용한다.
- 계좌 수정은 기존 `PUT /api/master/investment-accounts/{accountId}`를 그대로 사용한다.
- 메모 등록은 `investment_account_memo`에 INSERT만 수행하고, 계좌 기본정보의 `updated_at`이나 기존 메모 이력을 변경하지 않는다.
- 요청/응답 DTO 및 API URL을 변경하지 않는다.

## 6. Frontend 변경

### 6.1 상태 및 데이터 로딩

- `showMemoHistory`와 독립 메모 모달 전용 선택 상태(`selectedMemoAccount`)를 제거하거나 계좌 수정 모달의 편집 대상 상태로 대체한다.
- `openAccountEdit(item)`에서 계좌 상세를 성공적으로 조회한 뒤, 같은 `accountId`로 메모 이력을 조회한다.
- 메모 로딩 실패는 계좌 상세 조회 실패와 구분해 수정 팝업 안의 메모 영역에 표시한다. 계좌 기본정보 폼은 메모 조회 실패만으로 닫거나 비활성화하지 않는다.
- 수정 팝업을 닫거나 신규 계좌 등록 팝업을 열 때 메모 입력값, 목록, 로딩/저장 상태 및 메모 오류 상태를 초기화한다.

### 6.2 UI 구성

- `account-modal`을 세로 스크롤 가능한 구조로 조정하여 작은 화면에서도 계좌 정보와 메모 이력을 모두 접근 가능하게 한다.
- 기존 계좌 수정 폼의 하단(계좌 기본정보 저장 영역 다음)에 구분선과 `메모 이력` 섹션을 추가한다.
- 메모 섹션은 다음을 포함한다.
  - 새 메모 textarea
  - 메모 등록 버튼 및 등록 중 비활성화 상태
  - 메모 조회 중 상태, 빈 상태, 오류 상태
  - 최신순 메모 내용 및 작성 시각 목록
- HTML `form` 중첩을 만들지 않는다. 계좌 저장과 메모 등록은 각각 독립된 제출 동작으로 구성한다.
- 계좌 목록의 메모 이력 버튼과 `openMemoHistory`/독립 `memo-modal` 마크업 및 전용 스타일을 제거한다.
- 목록의 최신 메모 열, 계좌 수정 버튼, 상태 변경 버튼은 유지한다.

### 6.3 저장 동작

- 계좌 기본정보 저장은 현재와 동일한 payload 및 API를 사용한다.
- 메모 등록은 현재와 동일하게 공백을 제거한 뒤 빈 값이면 저장하지 않고 안내한다.
- 메모 등록 성공 시 `getAccountMemos(accountId)`와 `getAccounts(...)`를 다시 호출해 팝업 이력 및 목록의 최신 메모를 갱신한다.
- 계좌 기본정보 저장 성공 시 현재의 모달 닫기·목록/증권사 목록 새로고침 동작을 유지한다. 메모 등록만으로 계좌 수정 모달을 닫지 않는다.

## 7. DB 변경

DB 변경은 없다.

- 신규 테이블, 컬럼, 인덱스, 마이그레이션을 추가하지 않는다.
- `investment_account_memo`의 이력형 저장 구조와 `created_at DESC, memo_id DESC` 정렬을 유지한다.
- 기존 데이터의 수정·삭제·이관을 수행하지 않는다.

## 8. 기존 동작 유지 조건

- 증권사 및 계좌의 등록, 수정, 사용 상태 변경, 검색, 페이징은 기존과 동일하게 동작해야 한다.
- 계좌 수정 API의 요청 payload에는 메모를 포함하지 않는다.
- 계좌 목록은 계속 최신 메모 한 건만 표시하고, 메모가 없으면 `—`를 표시한다.
- 계좌 상세에서만 원본 계좌번호를 사용하는 현재 동작과 목록의 마스킹 동작을 유지한다.
- 메모는 생성 전용이다. 메모 수정, 삭제, 검색, 페이징 API 또는 UI를 추가하지 않는다.
- 존재하지 않는 계좌의 메모 조회/등록은 기존 404 처리, 공백 메모는 기존 400 처리 및 사용자 메시지를 유지한다.
- 메모 등록 성공 시 계좌 기본정보는 변경하지 않으며, 계좌의 `updated_at` 갱신을 유발하지 않는다.

## 9. 구현 제약

- 애플리케이션 소스 변경은 본 명세 승인 후에만 수행한다.
- 변경 범위는 계좌 관리 화면의 팝업 구성과 그 상태 관리로 한정한다. API 계약, 백엔드 비즈니스 로직, DB 스키마를 변경하지 않는다.
- 새 의존성, 새 라우트, 새 모달 컴포넌트는 추가하지 않는다.
- 기존 Vue 단일 파일 컴포넌트와 API 클라이언트의 구현 스타일을 따른다.
- 메모 영역은 편집 중인 기존 계좌에만 표시하며, 신규 계좌 등록에서 임시 메모를 보관하거나 자동 이관하지 않는다.

## 10. 검증 방법

### 정적/빌드 검증

1. 프론트엔드 타입 검사 또는 프로젝트의 `npm run build`를 실행해 Vue 템플릿과 TypeScript 오류가 없는지 확인한다.
2. 변경된 `AccountSettingsView.vue`에서 중첩 form, 사용하지 않는 상태/함수/import, 분리 메모 모달 참조가 남지 않았는지 확인한다.
3. 백엔드와 DB 소스에는 변경이 없는지 Git diff로 확인한다.

### 수동 기능 검증

1. 계좌 목록에서 수정 버튼을 누르면 하나의 계좌 수정 팝업이 열리고, 하단에 메모 입력 및 이력이 표시되는지 확인한다.
2. 메모가 있는 계좌에서 최신순 이력과 작성 시각이 표시되는지 확인한다.
3. 메모가 없는 계좌에서 빈 상태가 표시되는지 확인한다.
4. 유효한 메모를 등록하면 팝업을 닫지 않은 채 이력 최상단과 목록의 최신 메모가 갱신되는지 확인한다.
5. 공백 메모 등록 시 API 호출 없이 입력 안내가 표시되는지 확인한다.
6. 계좌 기본정보만 수정해 저장했을 때 기존 계좌 저장·목록 갱신·모달 닫기 동작이 유지되는지 확인한다.
7. 신규 계좌 등록 팝업에는 메모 영역이 표시되지 않는지 확인한다.
8. 계좌 목록에 메모 이력 전용 버튼이나 독립 메모 팝업이 더 이상 남아 있지 않은지 확인한다.

