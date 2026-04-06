# Frontend 규칙 (React + Vite)

## 디렉토리 역할
```
src/
├── api/        # 백엔드 API 호출 함수
├── components/ # 재사용 가능한 UI 컴포넌트
├── pages/      # 라우트 단위 페이지
└── utils/      # 공통 유틸 함수
```

## 규칙
- API 호출은 반드시 `api/` 디렉토리에 분리
- 컴포넌트는 `.jsx`, 유틸은 `.js`
- 백엔드 기본 주소: `http://localhost:8080`
- 의존성 추가: `npm install` 후 코드 작성
