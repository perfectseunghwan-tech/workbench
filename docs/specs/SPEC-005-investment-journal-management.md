# SPEC-005: 투자일지 작성 및 첨부파일 관리

## 1. 목적

`investment_journal`과 `investment_journal_file`을 사용하여 투자일지를 목록 조회, 상세 조회, 등록, 수정, 삭제할 수 있는 기능의 구현 방안을 정의한다. 일지 본문은 게시판형 편집 경험을 제공하며 일반 파일 첨부, 본문 이미지 삽입, 표 삽입을 지원한다. 일지 상세와 편집 화면 하단에는 일지 작성일과 같은 날짜의 기존 매수·매도 거래를 읽기 전용 표로 표시한다.

이 문서는 구현 전 분석·설계 명세이다. 작성 단계에서는 애플리케이션 소스와 DB를 변경하지 않는다.

## 2. 현재 구현 구조

### 2.1 Backend

- 백엔드는 Spring Boot 3.2.4, Java 21, PostgreSQL을 사용한다.
- 데이터 접근은 JPA 엔티티보다 `NamedParameterJdbcTemplate` 기반 Repository를 사용한다.
- 거래 기능은 `StockTradeController` → `StockTradeService` → `StockTradeRepository` 구조이다.
- 거래 API의 기준 경로는 `/api`이며, 거래 CRUD와 목록 검색을 제공한다.
  - `GET /api/stock-trades`
  - `GET /api/stock-trades/{tradeId}`
  - `POST /api/stock-trades`
  - `PUT /api/stock-trades/{tradeId}`
  - `DELETE /api/stock-trades/{tradeId}`
- 거래 목록 API는 `dateFrom`, `dateTo`, 증권사, 계좌, 종목, 시장, 키워드, 거래구분과 서버 페이징을 이미 지원한다.
- `dateFrom`과 `dateTo`에 같은 날짜를 전달하면 해당 거래일의 거래를 조회할 수 있다.
- `stock_trade.trade_type`은 현재 Service에서 `BUY` 또는 `SELL`만 허용한다.
- 거래 목록 응답에는 거래일, 매수·매도 구분, 증권사·계좌, 종목, 수량, 단가, 금액, 매입단가, 실현손익 및 메모가 포함된다.
- 업무 오류는 `ApiException`으로 표현하며 `ApiExceptionHandler`가 상태 코드와 `message`를 가진 JSON 오류 응답을 반환한다.
- 프로젝트 안에는 DB 마이그레이션 도구나 파일 업로드·파일 저장 서비스가 없다.
- `application.properties`에는 현재 파일 저장 위치 및 multipart 용량 제한에 관한 애플리케이션 설정이 없다.

### 2.2 Frontend

- 프론트엔드는 Vue 3, TypeScript, Vue Router, PrimeVue 4를 사용한다.
- `AppShell.vue`가 공통 사이드바와 상단 바를 제공하며 현재 거래 내역, 증권사·계좌 관리, 주식종목 관리, 서버 연결 확인 메뉴가 있다.
- `StockTradeView.vue`는 거래 목록 검색, 서버 페이징, 상세, 등록, 수정, 삭제를 한 화면에서 제공한다.
- `stockTradeApi.ts`의 `getTrades()`를 이용하면 날짜 범위와 거래구분을 포함한 기존 거래 목록을 재사용할 수 있다.
- 관리 화면은 PrimeVue `DataTable`, 공통 패널·버튼·모달·알림 스타일을 사용한다.
- 현재 프론트엔드 의존성에는 표와 이미지 노드를 포함한 구조화된 본문 편집기가 없다.

### 2.3 관련 DB 구조

사용자 제공 DDL을 투자일지 테이블 구조의 기준으로 사용한다. 프로젝트 안에는 해당 테이블을 생성하는 DDL 또는 마이그레이션 파일이 없다.

#### `public.investment_journal`

| 컬럼 | 타입 | 필수 | 기본값/의미 |
| --- | --- | --- | --- |
| `journal_id` | `bigserial` | 예 | 일지 기본키 |
| `journal_date` | `date` | 예 | 일지 기준일 |
| `journal_title` | `varchar(200)` | 아니오 | 일지 제목 |
| `content_format` | `varchar(20)` | 예 | 기본값 `JSON`, 허용값 `JSON`, `HTML`, `MARKDOWN` |
| `journal_content` | `jsonb` | 예 | 구조화된 일지 본문 |
| `created_at` | `timestamp` | 예 | 생성일시 |
| `updated_at` | `timestamp` | 예 | 수정일시 |

- `journal_date`에는 유일 제약조건이 없으므로 같은 날짜에 여러 일지를 저장할 수 있다.
- `journal_content`는 `jsonb NOT NULL`이므로 빈 본문도 유효한 JSON 문서로 저장해야 한다.
- `updated_at`은 자동 갱신 트리거가 명시되어 있지 않으므로 수정 SQL에서 직접 갱신해야 한다.

#### `public.investment_journal_file`

| 컬럼 | 타입 | 필수 | 기본값/의미 |
| --- | --- | --- | --- |
| `file_id` | `bigserial` | 예 | 파일 기본키 |
| `journal_id` | `int8` | 예 | 일지 외래키 |
| `file_type` | `varchar(20)` | 예 | `IMAGE` 또는 `FILE` |
| `original_file_name` | `varchar(255)` | 예 | 사용자가 업로드한 파일명 |
| `stored_file_name` | `varchar(255)` | 예 | 서버 저장 파일명 |
| `file_path` | `varchar(1000)` | 예 | 설정된 저장 루트 기준 상대 경로 |
| `mime_type` | `varchar(100)` | 아니오 | 확인된 MIME 타입 |
| `file_size` | `int8` | 아니오 | 바이트 단위 크기 |
| `sort_order` | `int4` | 예 | 기본값 `0`, 첨부 표시 순서 |
| `created_at` | `timestamp` | 예 | 생성일시 |

- `journal_id`는 `investment_journal.journal_id`를 참조하며 `ON DELETE CASCADE`이다.
- DB 연쇄 삭제는 파일 메타데이터 행만 삭제한다. 실제 파일 시스템의 파일은 애플리케이션이 별도로 정리해야 한다.
- `investment_journal`과 `stock_trade` 사이에는 직접 관계 컬럼이나 외래키가 없다.

### 2.4 분석 결과와 확정 방향

- 하루 여러 건은 `journal_id`로 구분하며 `journal_date`에 유일 제약조건을 추가하지 않는다.
- 일지와 거래를 별도 관계 테이블로 연결하지 않는다. 일지 하단 거래는 `stock_trade.trade_date = investment_journal.journal_date`인 행을 조회해 표시한다.
- 하단 거래 표는 참고용 읽기 전용이며 일지 저장·삭제가 거래 데이터에 영향을 주지 않는다.
- 이 기능에서 새로 저장하는 본문은 Tiptap 문서 JSON을 표준으로 하고 `content_format`은 `JSON`으로 저장한다. DDL의 `HTML`, `MARKDOWN` 값은 향후 호환을 위해 유지하되 이번 UI에서는 생성하지 않는다.
- 신규 작성 중 선택한 이미지와 첨부파일은 브라우저에서 임시 상태로 보관하고, 일지 저장 시 본문·파일 목록과 함께 multipart 요청으로 전송한다. 저장 전 서버 임시 파일이나 빈 일지 행은 만들지 않는다.
- 저장된 본문에는 서버 경로나 임시 Object URL을 기록하지 않고 해당 일지에 속한 `fileId`만 기록한다. 표시 시 파일 조회 API URL을 조립한다.

## 3. 요구사항

1. 투자일지를 목록 조회, 상세 조회, 등록, 수정, 삭제할 수 있어야 한다.
2. 한 날짜에 여러 일지를 작성할 수 있어야 하며 같은 날짜의 기존 일지 등록을 막지 않아야 한다.
3. 일지는 기준일, 선택 제목, 구조화된 본문, 생성일시 및 수정일시를 가진다.
4. 본문 편집기는 일반 문단과 제목, 굵게·기울임·목록·인용 등 기본 서식, 표 삽입·편집, 이미지 삽입을 지원해야 한다.
5. 일반 파일을 첨부하고 내려받을 수 있어야 한다.
6. 이미지 삽입 파일과 일반 첨부파일의 메타데이터는 `investment_journal_file`에 각각 `IMAGE`, `FILE`로 저장해야 한다.
7. 이미지와 첨부파일은 등록 및 수정 저장 전 미리 확인하고 제거하거나 순서를 바꿀 수 있어야 한다.
8. 일지 상세 및 편집 화면 하단에 일지 기준일과 같은 날짜의 매수·매도 기록을 표로 표시해야 한다.
9. 하단 거래 표는 기존 거래 데이터를 재사용하며 일지에서 거래를 등록, 수정 또는 삭제하지 않는다.
10. 일지를 삭제하면 해당 일지의 파일 메타데이터와 실제 저장 파일도 정리해야 한다.
11. 파일 조회 시 다른 일지의 `fileId`를 조합하여 접근할 수 없어야 한다.
12. 기존 거래, 기준정보 관리, 라우팅 및 API 동작은 유지해야 한다.

## 4. 변경 대상

| 구분 | 파일 | 변경 내용 |
| --- | --- | --- |
| Backend 신규 | `backend/src/main/java/com/example/backend/journal/InvestmentJournalController.java` | 일지 CRUD 및 파일 조회 API |
| Backend 신규 | `backend/src/main/java/com/example/backend/journal/InvestmentJournalDtos.java` | 저장 요청, 목록·상세·파일 응답, 검색 조건 DTO |
| Backend 신규 | `backend/src/main/java/com/example/backend/journal/InvestmentJournalService.java` | 검증, 트랜잭션, 본문 파일 참조 정규화, CRUD |
| Backend 신규 | `backend/src/main/java/com/example/backend/journal/InvestmentJournalRepository.java` | 일지·파일 메타데이터 SQL 및 페이징 조회 |
| Backend 신규 | `backend/src/main/java/com/example/backend/journal/JournalFileStorageService.java` | 안전한 파일명 생성, 저장, 조회, 삭제 |
| Backend 기존 | `backend/src/main/java/com/example/backend/trade/ApiExceptionHandler.java` | multipart 용량 초과 등 파일 요청 오류 응답 보완 |
| Backend 기존 | `backend/src/main/resources/application.properties` | 저장 루트와 multipart 제한 설정 |
| Backend 테스트 신규 | `backend/src/test/java/com/example/backend/journal/InvestmentJournalServiceTest.java` | 일지 검증·파일 참조·삭제 정책 단위 테스트 |
| Backend 테스트 신규 | `backend/src/test/java/com/example/backend/journal/InvestmentJournalControllerTest.java` | JSON 및 multipart API 계약 테스트 |
| Frontend 신규 | `frontend/src/services/investmentJournalApi.ts` | 일지·파일 API 타입과 호출 함수 |
| Frontend 신규 | `frontend/src/views/InvestmentJournalListView.vue` | 일지 검색·목록·삭제 화면 |
| Frontend 신규 | `frontend/src/views/InvestmentJournalDetailView.vue` | 일지 본문·첨부·당일 거래 상세 화면 |
| Frontend 신규 | `frontend/src/views/InvestmentJournalEditView.vue` | 신규·수정 작성 화면 |
| Frontend 신규 | `frontend/src/components/journal/JournalEditor.vue` | 구조화 본문, 표, 이미지 및 첨부 편집기 |
| Frontend 신규 | `frontend/src/components/journal/JournalTradeTable.vue` | 당일 매수·매도 읽기 전용 표 |
| Frontend 신규 | `frontend/src/utils/journalContent.ts` | 본문 기본값, 파일 참조 변환 및 표시 유틸리티 |
| Frontend 기존 | `frontend/src/router/index.ts` | 일지 목록·상세·작성·수정 라우트 추가 |
| Frontend 기존 | `frontend/src/views/AppShell.vue` | 투자일지 메뉴 추가 |
| Frontend 기존 | `frontend/src/assets/tailwind.css` | 공통으로 필요한 편집기·파일 표시 스타일만 보완 |
| Frontend 기존 | `frontend/package.json`, `frontend/package-lock.json` | Tiptap Vue 편집기와 표·이미지 확장 의존성 추가 |
| DB | 변경 파일 없음 | 제공된 두 테이블과 기존 `stock_trade` 사용 |

## 5. Backend 변경

### 5.1 API 계약

기준 경로는 `/api/investment-journals`로 한다.

| Method | 경로 | 기능 | 정상 응답 |
| --- | --- | --- | --- |
| `GET` | `/api/investment-journals` | 기간·제목 검색 및 서버 페이징 | `200 JournalPageResponse` |
| `GET` | `/api/investment-journals/{journalId}` | 본문과 파일을 포함한 상세 조회 | `200 JournalDetailResponse` |
| `POST` | `/api/investment-journals` | 일지와 신규 파일 등록 | `201 JournalDetailResponse`, `Location` 헤더 |
| `PUT` | `/api/investment-journals/{journalId}` | 일지, 유지 파일, 신규 파일 전체 수정 | `200 JournalDetailResponse` |
| `DELETE` | `/api/investment-journals/{journalId}` | 일지와 소속 파일 삭제 | `204` |
| `GET` | `/api/investment-journals/{journalId}/files/{fileId}` | 소속 확인 후 이미지 표시 또는 첨부 다운로드 | `200` 파일 스트림 |

#### 목록 조회

- 파라미터는 `dateFrom`, `dateTo`, `keyword`, `page`, `size`로 한다.
- `keyword`는 trim 후 `journal_title`에 대소문자 구분 없이 부분 일치시킨다. JSON 본문 전체 검색은 이번 범위에서 제외한다.
- `dateFrom`과 `dateTo`는 `journal_date`를 포함 범위로 검색한다.
- 시작일이 종료일보다 늦으면 HTTP 400을 반환한다.
- `page`는 0부터 시작하며 음수는 0으로 보정한다.
- `size` 기본값은 20이고 1~100 범위로 제한한다.
- 정렬은 `journal_date DESC, journal_id DESC`로 고정하여 같은 날 여러 건의 순서를 안정적으로 유지한다.

### 5.2 DTO

- `JournalSearchFilter`
  - `LocalDate dateFrom`
  - `LocalDate dateTo`
  - `String keyword`
  - `int page`
  - `int size`
- `JournalSaveRequest`
  - `LocalDate journalDate`
  - `String journalTitle`
  - `String contentFormat`
  - `JsonNode journalContent`
  - `List<Long> retainedFileIds`
  - `List<NewFileManifest> newFiles`
- `NewFileManifest`
  - `String uploadKey`
  - `String fileType`
  - `String originalFileName`
  - `int sortOrder`
- `JournalSummaryResponse`
  - `Long journalId`
  - `LocalDate journalDate`
  - `String journalTitle`
  - `int fileCount`
  - `LocalDateTime createdAt`
  - `LocalDateTime updatedAt`
- `JournalDetailResponse`
  - 목록 응답 필드
  - `String contentFormat`
  - `JsonNode journalContent`
  - `List<JournalFileResponse> files`
- `JournalFileResponse`
  - `Long fileId`
  - `String fileType`
  - `String originalFileName`
  - `String mimeType`
  - `Long fileSize`
  - `int sortOrder`
  - `LocalDateTime createdAt`
  - `String contentUrl`
- `JournalPageResponse`
  - `content`, `totalElements`, `totalPages`, `page`, `size`

### 5.3 등록·수정 요청 형식

- 등록과 수정은 `multipart/form-data`를 사용한다.
- `request` 파트에는 `JournalSaveRequest` JSON을 보낸다.
- `files` 파트에는 신규 파일을 `newFiles` 배열과 같은 순서로 반복 전송한다.
- `newFiles[i].uploadKey`는 저장 전 본문의 임시 이미지 노드와 업로드 파일을 연결하는 클라이언트 생성 UUID이다.
- `newFiles` 개수와 실제 `files` 파트 개수, 원본 파일명이 일치하지 않으면 HTTP 400을 반환한다.
- 신규 등록에서는 `retainedFileIds`가 비어 있어야 한다.
- 수정에서는 유지할 기존 파일 ID를 `retainedFileIds`로 전달한다. 여기에 없는 기존 파일은 저장 성공 후 삭제 대상으로 처리한다.
- 본문의 신규 이미지 노드는 저장 전 `uploadKey`를 가지며, 백엔드는 파일 저장 후 이를 해당 `fileId`로 치환한 정규화 JSON을 DB에 기록한다.
- DB에 저장되는 본문에는 `uploadKey`, `blob:` URL, 로컬 절대 경로를 남기지 않는다.

### 5.4 입력 검증

- `journalDate`는 필수이다.
- `journalTitle`은 선택값이며 trim 후 빈 문자열은 `null`로 저장하고 200자 이하여야 한다.
- 이 기능의 저장 요청은 `contentFormat`이 없으면 `JSON`을 적용하며 `JSON` 외의 값은 HTTP 400으로 거부한다.
- `journalContent`는 필수이며 최상위가 Tiptap `doc` 객체여야 한다.
- 빈 본문은 `{ "type": "doc", "content": [] }`로 정규화하여 저장할 수 있다.
- 허용된 본문 노드와 속성만 저장한다. 스크립트, 이벤트 속성, 임의 HTML, `data:` URL, 외부 이미지 URL은 허용하지 않는다.
- 저장된 이미지 노드의 `fileId`는 현재 일지에 속하고 `file_type = 'IMAGE'`인 파일만 참조할 수 있다.
- 신규 이미지 노드의 `uploadKey`는 같은 요청의 `IMAGE` manifest에 정확히 한 번 존재해야 한다.
- 수정 요청의 `retainedFileIds`는 모두 수정 대상 일지에 속해야 한다. 다른 일지의 파일 ID가 포함되면 HTTP 400을 반환한다.
- 존재하지 않는 `journalId`는 조회·수정·삭제 모두 HTTP 404로 처리한다.

### 5.5 본문 형식과 편집 기능

- 표준 저장 형식은 Tiptap JSON 문서이다.
- 최소 허용 노드는 `doc`, `paragraph`, `text`, `heading`, `bulletList`, `orderedList`, `listItem`, `blockquote`, `hardBreak`, `horizontalRule`, `table`, `tableRow`, `tableHeader`, `tableCell`, `journalImage`이다.
- 텍스트 mark는 `bold`, `italic`, `strike`, `code`를 지원한다.
- `journalImage`는 저장 후 `fileId`, `alt`, `title`만 가진다. 서버 파일 경로나 임시 브라우저 URL은 속성으로 저장하지 않는다.
- 표는 행·열 추가와 삭제, 헤더 셀 지정, 셀 병합·분할을 지원하는 Tiptap 표 확장을 사용한다.
- 서버는 JSON 직렬화 크기를 기본 2MB 이하로 제한한다. 이 값은 애플리케이션 설정으로 조정 가능하게 한다.
- 상세 화면은 JSON을 허용된 편집기 스키마의 읽기 전용 모드로 렌더링한다. `v-html`로 임의 HTML을 삽입하지 않는다.

### 5.6 파일 저장 정책

- 저장 루트는 `app.investment-journal.storage-root` 설정으로 분리한다. 개발 기본값은 백엔드 실행 위치 기준 `./data/investment-journal`로 한다.
- 실제 저장 경로는 `{journalId}/{storedFileName}` 구조로 만들고 `file_path`에는 저장 루트 기준 상대 경로만 기록한다.
- `storedFileName`은 서버에서 생성한 UUID와 검증된 확장자로 구성한다. 원본 파일명을 경로로 사용하지 않는다.
- 경로를 `normalize()`한 뒤 설정된 저장 루트 내부인지 확인하여 경로 이탈을 차단한다.
- 원본 파일명에서는 디렉터리 요소와 제어 문자를 제거하고 255자 제한을 적용한다.
- `IMAGE`는 JPEG, PNG, GIF, WebP만 허용한다. SVG는 스크립트 포함 위험 때문에 본문 이미지에서 제외한다.
- 이미지 유형은 요청의 Content-Type만 신뢰하지 않고 확장자와 실제 파일 시그니처를 함께 확인한다.
- `FILE`은 다운로드 시 항상 `Content-Disposition: attachment`로 응답하고, `X-Content-Type-Options: nosniff`를 설정한다.
- `IMAGE`는 허용된 이미지 MIME 타입으로 `Content-Disposition: inline` 응답한다.
- 기본 제한은 파일당 25MB, 요청 전체 100MB, 일지당 최대 30개로 한다. 제한값은 설정으로 관리한다.
- 0바이트 파일, 파일명이 없는 파트, 제한 초과 파일은 HTTP 400 또는 용량 초과 시 HTTP 413으로 거부한다.
- `file_size`에는 실제 저장된 바이트 수를 기록하고 `sort_order`는 음수가 되지 않도록 검증한다.

### 5.7 저장 트랜잭션과 파일 정리

- 일지 행과 파일 메타데이터 변경은 하나의 DB 트랜잭션에서 처리한다.
- 신규 파일은 검증 후 저장 루트 내부의 작업 경로에 먼저 기록하고, DB 저장과 본문 `fileId` 치환이 모두 성공한 경우 최종 경로로 이동한다.
- DB 처리 또는 파일 이동이 실패하면 이번 요청에서 새로 만든 파일을 보상 삭제하고 DB 트랜잭션을 롤백한다.
- 수정 시 기존 파일은 새 본문과 파일 메타데이터 저장이 성공하기 전에 삭제하지 않는다.
- 수정에서 제거된 파일과 일지 삭제 시의 실제 파일은 DB 커밋 이후 삭제한다. 삭제 실패는 구조화 로그로 남겨 DB 복구를 방해하지 않으며, 운영자가 정리할 수 있도록 `journalId`, `fileId`, 상대 경로만 기록한다.
- 로그에는 파일 내용, 사용자 입력 본문, 서버 절대 저장 루트를 출력하지 않는다.
- 파일 조회는 Repository에서 `(journal_id, file_id)` 조합으로 메타데이터를 찾은 후에만 수행한다.

### 5.8 Repository SQL

- 목록은 일지 행과 파일 수를 조회하되 N+1 조회를 피하도록 파일 수 집계 서브쿼리 또는 그룹 조회를 사용한다.
- 상세 조회는 일지 한 건을 먼저 조회하고 해당 `journal_id`의 파일을 `sort_order, file_id` 순으로 조회한다.
- `journal_content` 저장 시 문자열 연결 SQL을 사용하지 않고 파라미터를 `jsonb`로 캐스팅한다.
- 수정 SQL은 `journal_date`, `journal_title`, `content_format`, `journal_content`를 갱신하고 `updated_at = CURRENT_TIMESTAMP`를 명시한다.
- 삭제 전에 실제 파일 정리에 필요한 파일 메타데이터를 조회한 뒤 일지 행을 삭제한다. DB 파일 행은 기존 `ON DELETE CASCADE`를 사용한다.
- 같은 날짜의 기존 일지 존재 여부를 검사하거나 등록을 차단하는 SQL은 추가하지 않는다.

### 5.9 당일 거래 연동

- 일지 모듈에 거래 복제 테이블, 연결 테이블 또는 별도 거래 CRUD API를 만들지 않는다.
- 프론트엔드는 기존 `GET /api/stock-trades`를 다음과 같이 호출한다.
  - `dateFrom={journalDate}`
  - `dateTo={journalDate}`
  - `page={현재 페이지}`
  - `size=20`
- 매수와 매도를 함께 표시하며 필요하면 화면 안에서 `tradeType`을 `BUY`, `SELL`로 선택해 기존 필터를 전달한다.
- 거래 응답 계약과 거래 검증 로직은 변경하지 않는다.

### 5.10 오류 처리

- 기존 오류 JSON 구조를 유지한다.
- multipart 요청 크기 초과는 HTTP 413과 `첨부파일 전체 크기가 허용 범위를 초과했습니다.` 메시지로 변환한다.
- 개별 파일 형식, 개수, 파일명, 본문 참조 오류는 HTTP 400으로 처리한다.
- 존재하지 않는 일지 또는 파일은 HTTP 404로 처리한다.
- 파일 저장 장치 오류는 HTTP 500으로 처리하되 내부 경로나 예외 상세는 응답에 노출하지 않는다.

## 6. Frontend 변경

### 6.1 라우트와 메뉴

- `router/index.ts`에 다음 자식 라우트를 추가한다.
  - `/journals` / `investment-journal-list` / `InvestmentJournalListView.vue`
  - `/journals/new` / `investment-journal-create` / `InvestmentJournalEditView.vue`
  - `/journals/:journalId` / `investment-journal-detail` / `InvestmentJournalDetailView.vue`
  - `/journals/:journalId/edit` / `investment-journal-edit` / `InvestmentJournalEditView.vue`
- 각 라우트의 제목은 목록 `투자일지`, 신규 `투자일지 작성`, 상세 `투자일지 상세`, 수정 `투자일지 수정`으로 한다.
- `AppShell.vue`의 `투자 관리` 그룹에서 거래 내역 다음에 `투자일지` 메뉴를 추가한다.
- 메뉴 아이콘은 설치된 PrimeIcons에서 일지 의미에 맞는 `pi pi-book`을 사용한다.

### 6.2 API 모듈

- `investmentJournalApi.ts`에 Backend DTO와 대응하는 타입을 정의한다.
- 다음 함수를 제공한다.
  - `getJournals(params)`
  - `getJournal(journalId)`
  - `createJournal(payload, files)`
  - `updateJournal(journalId, payload, files)`
  - `deleteJournal(journalId)`
  - `getFileUrl(journalId, fileId)`
- 저장 함수는 `FormData`를 만들고 브라우저가 multipart boundary를 설정하도록 `Content-Type` 헤더를 직접 지정하지 않는다.
- 오류 응답의 `message`를 우선 표시하는 현재 API 모듈 패턴을 유지한다.

### 6.3 일지 목록 화면

- 상단에 기간 시작일, 종료일, 제목 키워드, 조회, 초기화, `일지 작성` 버튼을 제공한다.
- 목록은 PrimeVue `DataTable`의 lazy 서버 페이징을 사용한다.
- 열은 일지일자, 제목, 첨부 수, 작성일시, 수정일시, 관리로 구성한다.
- 제목이 없는 일지는 `제목 없음`으로 표시하되 저장 데이터는 변경하지 않는다.
- 행 클릭 또는 상세 버튼으로 상세 라우트에 이동한다.
- 관리 열에 수정과 삭제 버튼을 제공한다.
- 삭제 전 일지 날짜와 제목을 포함한 확인 메시지를 표시한다.
- 삭제 성공 후 현재 페이지의 마지막 행이 없어졌으면 앞 페이지로 이동한 뒤 재조회한다.
- 로딩, 오류, 검색 결과 없음 상태를 구분하여 표시한다.

### 6.4 작성·수정 화면

- 작성 화면의 기본 일지는 브라우저 현지 날짜이고 제목은 빈 값, 본문은 빈 Tiptap 문서이다.
- 입력 항목은 일지일자, 제목, 본문 편집기, 첨부파일 영역으로 구성한다.
- 일지일자는 필수이고 제목은 선택이며 `maxlength=200`을 적용한다.
- 수정 화면은 `journalId` 상세를 조회해 본문과 파일 목록을 초기화한다.
- 저장 중에는 중복 제출을 막는다.
- 등록 성공 시 서버가 반환한 `journalId`의 상세 화면으로 이동한다.
- 수정 성공 시 같은 일지의 상세 화면으로 이동한다.
- 취소 시 변경 내용이 있으면 이탈 확인을 표시하고, 변경이 없으면 목록 또는 상세 화면으로 바로 돌아간다.
- 저장 실패 시 편집 내용과 선택 파일을 유지하고 오류를 화면 상단에 표시한다.

### 6.5 본문 편집기

- Vue 3 연동과 JSON 출력, 표 확장성이 있는 Tiptap을 사용한다.
- 최소 의존성은 `@tiptap/vue-3`, `@tiptap/starter-kit`, `@tiptap/extension-table`, `@tiptap/extension-table-row`, `@tiptap/extension-table-header`, `@tiptap/extension-table-cell`이며 이미지 파일 노드는 프로젝트 내부 확장으로 정의한다.
- 툴바는 문단·제목, 굵게, 기울임, 취소선, 글머리·번호 목록, 인용, 구분선, 표 삽입·행/열 조작, 이미지 삽입, 실행 취소·다시 실행을 제공한다.
- 이미지 선택 즉시 `URL.createObjectURL()`로 미리보기를 제공하고 문서에는 임시 `uploadKey`를 가진 `journalImage` 노드를 넣는다.
- 이미지 제거 또는 화면 종료 시 사용하지 않는 Object URL을 `URL.revokeObjectURL()`로 해제한다.
- 붙여넣기 또는 드래그앤드롭 이미지도 파일 선택과 동일한 검증·업로드 대기 흐름을 사용한다.
- 외부 HTML 붙여넣기는 지원 노드와 텍스트 서식만 남기고 이미지 URL, 스크립트, 스타일 및 이벤트 속성을 제거한다.
- 표와 이미지는 모바일에서 가로 스크롤 또는 컨테이너 폭 축소가 가능해야 하며 페이지 전체 레이아웃을 넘치지 않아야 한다.

### 6.6 첨부파일 영역

- `파일 첨부` 버튼으로 여러 파일을 선택할 수 있다.
- 기존 파일과 신규 파일을 한 목록에 표시하며 파일명, 크기, 유형, 순서, 제거 동작을 제공한다.
- 신규 파일 제거는 브라우저 상태에서만 제거하고 서버 요청을 발생시키지 않는다.
- 기존 파일 제거는 즉시 삭제하지 않고 수정 저장 시 `retainedFileIds`에서 제외한다. 사용자가 수정을 취소하면 기존 파일은 유지된다.
- 이미지 노드를 본문에서 제거한 경우 해당 이미지 파일도 유지 목록과 신규 파일 목록에서 제거한다.
- 일반 첨부는 본문 노드가 없어도 첨부 목록에 존재할 수 있다.
- 파일 제한을 클라이언트에서도 안내·검사하되 백엔드 검증을 최종 기준으로 한다.

### 6.7 상세 화면

- 일지일자, 제목, 생성일시, 수정일시를 표시한다.
- 본문은 편집기와 같은 스키마를 읽기 전용으로 렌더링한다.
- 본문 이미지 URL은 `journalId`와 `fileId`로 파일 API URL을 만들어 사용한다.
- 일반 첨부는 파일명, 크기와 다운로드 버튼을 제공한다.
- 수정, 삭제, 목록으로 버튼을 제공한다.
- 삭제 성공 시 일지 목록으로 이동하고 완료 메시지를 전달한다.

### 6.8 당일 매수·매도 표

- 상세 화면과 작성·수정 화면의 본문·첨부 영역 아래에 `당일 매수·매도 기록` 섹션을 둔다.
- 작성 화면에서는 선택한 `journalDate`가 바뀔 때 해당 날짜로 거래 목록을 다시 조회한다.
- 날짜 입력이 비어 있으면 거래 API를 호출하지 않고 날짜 선택 안내를 표시한다.
- 열은 구분, 종목, 증권사·계좌, 체결수량, 체결단가, 체결금액, 매입단가, 실현손익, 거래메모로 구성한다.
- `BUY`는 `매수`, `SELL`은 `매도`로 표시하고 기존 거래 화면의 금액·배지 표현을 재사용한다.
- 거래가 20건을 초과하면 표 내부 서버 페이징을 사용한다.
- 전체/매수/매도 필터를 제공하되 기본값은 전체이다.
- 표에는 거래 수정·삭제 버튼을 두지 않는다.
- 같은 날짜에 여러 일지가 있으면 각 일지에서 동일한 당일 거래 목록이 표시된다. 이는 날짜 기반 참고 정보이며 일지별 소유 관계를 의미하지 않는다.
- 일지 저장 실패나 삭제는 `stock_trade`에 어떤 변경도 발생시키지 않는다.

### 6.9 화면 상태와 접근성

- 일지 조회 로딩, 거래 조회 로딩, 파일 처리, 저장 상태를 분리한다.
- 편집기 툴바 버튼에는 기능을 설명하는 `aria-label`과 활성 상태를 제공한다.
- 파일 선택 input은 접근 가능한 label을 가지며 키보드로 조작할 수 있어야 한다.
- 삭제나 저장 오류는 색상만으로 구분하지 않고 텍스트 메시지로 알린다.
- 이미지에는 원본 파일명을 기본 대체 텍스트로 사용하고 사용자가 대체 텍스트를 수정할 수 있게 한다.
- 기존 공통 색상, 간격, 버튼, DataTable 및 반응형 패턴을 우선 재사용한다.

## 7. DB 변경

### 7.1 이번 구현 범위

- 테이블 생성, 컬럼 추가, 제약조건 변경 및 데이터 마이그레이션은 수행하지 않는다.
- 사용자 제공 `investment_journal`, `investment_journal_file`을 그대로 사용한다.
- `journal_date`에 유일 제약조건을 추가하지 않는다.
- `stock_trade`에 `journal_id`를 추가하지 않는다.
- 일지 삭제 시 `investment_journal_file` 메타데이터 삭제는 기존 `ON DELETE CASCADE`를 사용한다.
- 실제 파일은 DB가 아닌 애플리케이션 저장소에서 별도로 삭제한다.

### 7.2 인덱스 고려사항

- 초기 구현의 정확성을 위해 추가 인덱스는 필수 조건으로 두지 않는다.
- 데이터가 증가하여 목록 또는 파일 조회 성능이 저하되면 다음 인덱스를 별도 운영 변경으로 검토한다.
  - `investment_journal (journal_date DESC, journal_id DESC)`
  - `investment_journal_file (journal_id, sort_order, file_id)`
- 인덱스 생성은 이번 애플리케이션 구현에서 자동 실행하지 않는다.

## 8. 기존 동작 유지 조건

- 기존 `/api/stock-trades` 요청·응답, 필터, 정렬, 페이징과 CRUD 동작을 변경하지 않는다.
- 일지 하단 거래 조회를 위해 기존 거래 데이터를 복제하거나 수정하지 않는다.
- 거래 삭제가 일지를 삭제하거나 일지 본문을 변경하지 않는다.
- 거래일 변경으로 과거 일지에서 보이는 거래 집합이 달라질 수 있으며, 이는 날짜 기반 조회의 의도된 동작이다.
- 기존 증권사·계좌 및 주식종목 관리 API와 화면을 변경하지 않는다.
- 현재 홈 경로 `/`와 거래 내역 메뉴의 의미를 유지한다.
- `ApiExceptionHandler` 보완 후에도 기존 오류 응답의 필드와 기존 상태 코드를 유지한다.
- 공통 CSS 수정은 새 일지 화면에 필요한 선택자 위주로 제한하고 기존 화면의 레이아웃과 컴포넌트 표현을 바꾸지 않는다.
- 기존 사용자 작업이 있는 `StockTradeView.vue`는 일지 구현을 위해 수정하지 않고 `JournalTradeTable.vue`에서 API 타입과 표현 패턴만 재사용한다.

## 9. 구현 제약

- 구현은 현재 Controller → Service → Repository 구조와 `NamedParameterJdbcTemplate` 패턴을 따른다.
- DB 스키마 자동 생성이나 새 마이그레이션 프레임워크를 도입하지 않는다.
- 일지 본문은 이번 기능에서 `JSON`만 생성한다. `HTML`과 `MARKDOWN` 저장·편집 UI는 범위 밖이다.
- 원본 파일 바이너리를 `jsonb` 또는 DB bytea로 저장하지 않는다.
- DB의 `file_path`를 브라우저에 직접 반환하지 않는다.
- 파일을 프론트엔드 `public` 디렉터리 또는 애플리케이션 클래스패스 정적 리소스에 저장하지 않는다.
- 파일 조회는 반드시 `journalId`와 `fileId`의 소속 관계를 확인한다.
- 외부 URL 이미지를 본문에 직접 삽입하는 기능은 범위 밖이다.
- 신규 작성 취소 시 서버에 일지 행이나 임시 파일이 남지 않아야 한다.
- 일지별 거래를 임의로 선택해 연결하는 기능, 거래 스냅샷 저장, 손익 합계 재계산은 범위 밖이다.
- 인증·사용자별 소유권 컬럼과 접근 제어는 현재 스키마와 애플리케이션에 사용자 인증 구조가 없으므로 이번 범위에 추가하지 않는다.
- Tiptap 관련 패키지는 표와 구조화 JSON 편집에 필요한 최소 패키지만 추가하고 다른 UI 프레임워크는 추가하지 않는다.
- 파일 제한과 저장 루트는 코드 상수가 아니라 설정값으로 관리하되 안전한 기본값을 둔다.
- DB 트랜잭션과 파일 시스템 작업은 완전한 원자 트랜잭션이 될 수 없으므로 보상 삭제와 커밋 이후 정리 정책을 명시적으로 구현한다.

## 10. 구현 순서

1. 일지 DTO와 Repository의 목록·상세·저장·수정·삭제 SQL을 구현한다.
2. 안전한 파일 저장·조회·정리 서비스를 구현한다.
3. Service에서 본문 스키마, 파일 manifest, 소속 관계, 크기·형식을 검증하고 트랜잭션 및 보상 처리를 연결한다.
4. Controller의 목록·상세·multipart 저장·파일 스트림 API와 오류 처리를 구현한다.
5. Backend 단위 및 API 계약 테스트를 작성한다.
6. 프론트엔드 API 타입·호출 모듈과 본문 변환 유틸리티를 구현한다.
7. Tiptap 기반 편집기와 파일 대기열을 구현한다.
8. 일지 목록, 상세, 작성·수정 화면과 라우트를 구현한다.
9. 기존 거래 API를 사용하는 당일 거래 표를 연결한다.
10. 메뉴와 최소 공통 스타일을 반영하고 타입 검사·빌드 및 수동 시나리오를 검증한다.

## 11. 검증 방법

### 11.1 Backend 단위 테스트

1. 같은 `journalDate`로 두 건을 등록해 서로 다른 `journalId`가 생성되는지 확인한다.
2. 제목 trim, 빈 제목의 `null` 변환, 200자 초과 거부를 확인한다.
3. 날짜 누락, 잘못된 content format, 잘못된 최상위 JSON 문서를 HTTP 400으로 거부하는지 확인한다.
4. 빈 Tiptap 문서가 정상 저장되는지 확인한다.
5. 허용되지 않은 노드, 외부 이미지 URL, `data:` URL, 알 수 없는 파일 참조를 거부하는지 확인한다.
6. 신규 이미지의 `uploadKey`가 manifest 및 multipart 파일과 정확히 매핑되는지 확인한다.
7. 다른 일지의 `fileId`를 본문 또는 `retainedFileIds`에 넣은 요청을 거부하는지 확인한다.
8. JPEG, PNG, GIF, WebP 이미지는 허용하고 SVG 또는 위장된 이미지 파일은 거부하는지 확인한다.
9. 파일당 크기, 요청 전체 크기, 파일 개수 제한을 확인한다.
10. 수정에서 유지한 파일은 남고 제외한 파일은 DB 커밋 이후 정리되는지 확인한다.
11. 일지 삭제 시 DB 행과 파일 메타데이터가 삭제되고 실제 파일 정리가 요청되는지 확인한다.
12. 저장 실패 시 신규 파일 보상 삭제와 DB 롤백이 수행되는지 확인한다.
13. `(journalId, fileId)`가 일치하지 않는 파일 조회가 HTTP 404인지 확인한다.
14. 목록의 날짜 범위, 제목 검색, 정렬, 페이징 및 첨부 수가 정확한지 확인한다.

### 11.2 Backend API 계약 테스트

1. 파일 없는 multipart 등록과 파일을 포함한 multipart 등록 응답 및 `Location` 헤더를 확인한다.
2. 수정 응답의 본문 이미지 노드가 `uploadKey` 대신 생성된 `fileId`를 포함하는지 확인한다.
3. `IMAGE` 응답의 Content-Type과 inline disposition을 확인한다.
4. `FILE` 응답의 attachment disposition, 안전하게 인코딩된 원본 파일명, `nosniff` 헤더를 확인한다.
5. 용량 초과가 기존 오류 JSON 형식의 HTTP 413으로 반환되는지 확인한다.
6. 존재하지 않는 일지의 상세·수정·삭제와 파일 조회가 HTTP 404인지 확인한다.

### 11.3 Frontend 정적 검증

- `frontend`에서 `npm run type-check`를 실행한다.
- 타입 검사가 성공하면 `npm run build-only`를 실행한다.
- 새 의존성 설치 후 lock 파일이 일관되게 갱신되었는지 확인한다.
- 기존 거래, 계좌 관리, 종목 관리 라우트의 컴파일 오류가 없는지 확인한다.

### 11.4 수동 기능 검증

1. 같은 날짜에 제목과 내용이 다른 일지를 여러 건 등록하고 모두 목록에 표시되는지 확인한다.
2. 제목 없는 빈 본문 일지와 일반 본문 일지를 각각 등록할 수 있는지 확인한다.
3. 제목·목록·인용·표를 섞어 저장한 뒤 상세 및 수정 화면에서 구조가 유지되는지 확인한다.
4. 본문 이미지 삽입, 미리보기, 저장, 재조회, 수정 중 제거를 확인한다.
5. 여러 일반 파일의 첨부, 순서 변경, 다운로드, 수정 중 제거를 확인한다.
6. 신규 작성에서 파일을 선택한 뒤 취소했을 때 서버에 일지나 파일이 생성되지 않는지 확인한다.
7. 수정 중 기존 파일을 제거했다가 저장하지 않고 취소하면 파일이 유지되는지 확인한다.
8. 일지 기준일을 바꿀 때 하단 거래가 해당 날짜로 다시 조회되는지 확인한다.
9. 한 날짜의 매수와 매도가 함께 표시되고 전체/매수/매도 필터 및 내부 페이징이 동작하는지 확인한다.
10. 같은 날짜의 여러 일지에서 동일한 당일 거래가 표시되는지 확인한다.
11. 일지 삭제 후 상세 접근이 404이고 첨부 파일 URL도 접근되지 않는지 확인한다.
12. 모바일 폭에서 편집기 툴바, 표, 이미지, 첨부 목록과 거래 표가 화면 밖으로 깨지지 않는지 확인한다.

### 11.5 회귀 검증

- 기존 거래 목록 조회, 거래 상세, 등록, 수정, 삭제를 확인한다.
- 기존 날짜·종목·계좌·거래구분 필터와 거래 페이징을 확인한다.
- 증권사·계좌 관리 및 주식종목 관리 화면의 조회·저장 동작을 확인한다.
- 기존 홈, 설정, 연결 확인 라우트와 사이드바 접기·모바일 메뉴 동작을 확인한다.

## 12. 완료 기준

- 동일 날짜에 복수 일지를 독립적으로 CRUD할 수 있다.
- 일지 본문의 기본 서식, 표, 저장형 이미지가 재조회 후 동일하게 표현된다.
- 일반 파일을 첨부·다운로드·제거할 수 있다.
- 저장된 본문과 파일 메타데이터에 임시 URL이나 서버 절대 경로가 노출되지 않는다.
- 일지 하단에 같은 날짜의 매수·매도 거래가 기존 API를 통해 읽기 전용으로 표시된다.
- 일지와 파일 작업이 기존 거래 데이터 및 기존 관리 기능을 변경하지 않는다.
- Backend 대상 테스트와 Frontend 타입 검사·빌드가 통과한다.
