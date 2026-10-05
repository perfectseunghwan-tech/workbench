# 로컬 연결 확인

Java 21 및 frontend/package.json의 Node 버전 조건을 충족하는 Node.js가 필요합니다.
기존 backend 설정에 맞게 PostgreSQL과 OPENAI_API_KEY를 준비합니다.

첫 번째 터미널 (프로젝트 루트):
```powershell
cd backend
.\gradlew.bat bootRun
```

두 번째 터미널 (프로젝트 루트):
```powershell
npm --prefix frontend ci
npm run local
```

frontend 폴더에서도 npm run local을 사용할 수 있습니다.
브라우저가 자동으로 열리고 /api/connection의 실제 응답에 따라 연결 성공 또는 실패를 표시합니다.
5초 제한과 재시도 버튼을 제공합니다.
npm run local은 프론트엔드를 실행하며 백엔드는 별도 실행해야 합니다.
API 요청은 Vite proxy를 통해 http://127.0.0.1:8080으로 전달됩니다.
포트 변경 시 frontend/vite.config.ts의 proxy 대상도 수정하세요.
이 API는 HTTP 연결만 확인하며 DB나 외부 AI 서비스의 상태는 검사하지 않습니다.
