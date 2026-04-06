# BookCard 프로젝트 규칙

## 핵심 문서 참조
- 전체 스펙: `PROJECT_SPECIFICATION.md`
- 개발 순서/가이드: `DEVELOPMENT_GUIDE.md`
- 프론트엔드 규칙: `frontend/CLAUDE.md`

## 패키지 구조
```
com.example.bookcard/
├── controller/   # REST API 엔드포인트
├── service/      # 비즈니스 로직
├── repository/   # JPA Repository
├── entity/       # DB 엔티티
├── dto/          # 요청/응답 DTO
└── config/       # 설정 클래스
```

## 규칙
- 빌드: `./gradlew build`, 실행: `./gradlew bootRun`
- 프론트: `frontend/` 디렉토리에서 `npm run dev`
- 외부 API 키는 환경변수로만 관리 (코드에 하드코딩 금지)
- 새 기능 추가 시 controller → service → repository 순서로 구현
