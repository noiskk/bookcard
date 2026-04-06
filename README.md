# 📚 BookCard

> AI가 책을 읽고, 북카드를 만들어줍니다.

책 제목을 검색하면 GPT가 분위기를 분석하고 감성적인 5줄 요약을 작성합니다.<br>
Gemini가 그 분위기에 맞는 커버 이미지를 생성하고, 하나의 북카드로 저장됩니다.

---

## 목차

- [생성 예시](#-생성-예시)
- [핵심 기능](#-핵심-기능)
- [기술 스택](#-기술-스택)
- [아키텍처](#-아키텍처)
- [시작하기](#-시작하기)
- [API 명세](#-api-명세)
- [성능](#-성능)
- [프로젝트 문서](#-프로젝트-문서)

<br>

---

## ✨ 생성 예시

<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/images/example-채식주의자.png" width="220" alt="채식주의자 북카드"/>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/example-작별하지않는다.png" width="220" alt="작별하지 않는다 북카드"/>
    </td>
    <td align="center" width="33%">
      <img src="docs/images/example-해리포터비밀의방.png" width="220" alt="해리포터 비밀의 방 북카드"/>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <b>채식주의자</b> · 한강<br/><br/>
      <i>
        육체를 거부하는 순간, 그녀의 세계는 산산이 부서졌다.<br/>
        왜 우리는 폭력을 당연히 받아들이는 걸까?<br/>
        살아 있는 모든 것들이 무겁게 느껴진다면,<br/>
        이 책이 그 답이 될 수 있다.<br/>
        정신의 깊은 곳에서 피어오르는 불안과 고뇌.<br/>
        그리고 다시, 처음처럼.
      </i>
    </td>
    <td align="center" valign="top">
      <b>작별하지 않는다</b> · 한강<br/><br/>
      <i>
        사랑은 불타오르는 기억 속에서도 희미해지지 않는다.<br/>
        한강의 문장은 차가운 눈 속에 숨겨진 따뜻한 불꽃과 같다.<br/>
        비극적인 역사의 잔해 속,<br/>
        우리는 무엇을 기억해야 할까?<br/>
        그녀는 선택했다, 사라진 이들을 떠나보내지 않기로.<br/>
      </i>
    </td>
    <td align="center" valign="top">
      <b>해리 포터와 비밀의 방</b> · J.K. 롤링<br/><br/>
      <i>
        비밀의 방이 열렸다. 이젠 해리의 차례다.<br/>
        믿었던 그가 적이라면,<br/>
        그 배신의 무게는 얼마나 클까?<br/>
        어두운 복도를 지나, 미스터리한 소리가 그를 부른다.<br/>
        우정과 용기가 빛나는 순간,<br/>
        해리는 진정한 마법을 깨닫게 된다.
      </i>
    </td>
  </tr>
</table>

> GPT-4o가 책의 장르·분위기·테마를 분석하고, Gemini가 그에 맞는 커버 이미지를 생성합니다.

<br>

---

## 🎯 핵심 기능

### AI 프롬프트 체이닝

단일 프롬프트 대신 **4단계 체이닝**으로 품질을 높입니다.

```
[1단계] 책 분석 — GPT-4o가 장르·분위기·테마·감정을 추출  (~6.4초)
    ↓ 분석 결과 전달
[2단계] 한글 요약 — 책을 읽고 싶게 만드는 5문장 생성       (~1.8초)
    ↓ 요약 + 분석 결과 전달
[3단계] 이미지 프롬프트 — 분위기에 맞는 영문 프롬프트 생성  (~1.7초)
    ↓ 프롬프트 전달
[4단계] 이미지 생성 — Gemini가 커버 아트 생성              (~11.3초)
```

각 단계의 출력이 다음 단계의 입력이 되어 풍부한 컨텍스트를 유지합니다.

<br>

### SSE 실시간 진행률

생성 중 단계별 메시지를 Server-Sent Events로 실시간 전송합니다.
약 21초의 대기 시간 동안 사용자가 진행 상황을 확인할 수 있어 이탈을 방지합니다.

WebSocket 대신 SSE를 선택한 이유: 서버→클라이언트 **단방향** 통신으로 충분하며,
HTTP 기반이라 방화벽에 친화적입니다.

<br>

### JWT 인증 + UX 보호

- 회원가입/로그인 시 JWT 발급 (유효기간 24시간, HS384)
- `JwtAuthFilter`가 모든 요청의 `Authorization: Bearer` 헤더를 검증
- 비회원도 북카드 조회·검색 가능 / 생성·삭제·좋아요는 인증 필요
- **비로그인 생성 시도** → `/login`으로 즉시 리다이렉트 (401 에러 노출 방지)
- **토큰 만료 시** → 401 인터셉터가 자동 로그아웃 후 로그인 페이지 이동
- **헤더 닉네임 표시** → 로그인 상태를 헤더에서 즉시 확인 가능

<br>

### 좋아요 — 중복 방지 토글

- `book_likes` 테이블에 `(book_id, user_id)` 복합 유니크 제약으로 중복 방지
- 동일 사용자가 다시 누르면 취소 (토글), `likeCount`는 실제 레코드 수로 동기화

<br>

### 동시성 처리

- **ISBN 중복 생성**: DB UNIQUE 제약 + 애플리케이션 레벨 중복 체크
- **SSE 스레드풀**: `CachedThreadPool` + 비동기 스레드에 SecurityContext 전파

<br>

---

## 🛠 기술 스택

### Backend
| | 기술 | 버전 |
|---|---|---|
| 언어 | Java | 17 |
| 프레임워크 | Spring Boot | 3.5.9 |
| ORM | Spring Data JPA | - |
| 인증 | Spring Security + jjwt | 0.12.6 |
| AI (텍스트) | Spring AI + GPT-4o | 1.0.0-M6 |
| AI (이미지) | Google GenAI SDK + Gemini | 1.2.0 |
| 빌드 | Gradle | 8.14.3 |

### Frontend
| | 기술 | 버전 |
|---|---|---|
| UI | React | 19.2.0 |
| 번들러 | Vite | 7.2.4 |
| 스타일 | Tailwind CSS | 4.1.18 |
| 라우팅 | React Router | 7.12.0 |

### Database & Infra
| 환경 | DB |
|---|---|
| 개발 (`dev` profile) | H2 In-Memory |
| 운영 (`prod` profile) | MySQL 8.x |

### 외부 API
| API | 용도 |
|---|---|
| [Naver Books API](https://developers.naver.com/docs/serviceapi/search/book/book.md) | 도서 검색 |
| [OpenAI GPT-4o](https://platform.openai.com/docs/models/gpt-4o) | 책 분석 · 요약 · 이미지 프롬프트 |
| [Google Gemini](https://ai.google.dev/gemini-api/docs/image-generation) | 커버 이미지 생성 |

<br>

---

## 🏗 아키텍처

```
Browser (React :5173)
    │  HTTP / SSE  │  Authorization: Bearer JWT
    ▼
Spring Boot (:8080)
    ├── JwtAuthFilter          → SecurityContext 인증 정보 저장
    ├── BookController         → SSE Emitter + ThreadPoolExecutor
    │    └── BookService
    │         ├── OpenAiService  (4단계 프롬프트 체이닝)
    │         │    ├── GPT-4o   [Spring AI ChatClient]
    │         │    └── Gemini   [Google GenAI SDK]
    │         ├── NaverSearchService
    │         └── ImageStorageService  → uploads/images/UUID.png
    └── AuthController
         └── AuthService  → BCrypt + JwtUtil

MySQL / H2
```

### 패키지 구조

```
com.example.bookcard/
├── config/       # Security · JWT · AI · CORS · 예외처리
├── controller/   # REST 엔드포인트
├── service/      # 비즈니스 로직 · 트랜잭션 경계
├── repository/   # JPA Repository
├── entity/       # Book · User · BookLike
└── dto/          # 요청/응답 DTO
```

<br>

---

## 🚀 시작하기

### 사전 준비

- Java 17+
- MySQL 8.x (운영 환경)
- API 키: [OpenAI](https://platform.openai.com/api-keys) · [Google Gemini](https://aistudio.google.com/app/apikey) · [Naver Developers](https://developers.naver.com/apps/)

### 환경 변수 설정

```bash
cp .env.example .env
```

```env
# Database
DB_USERNAME=root
DB_PASSWORD=yourpassword

# API Keys
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=AIza...
NAVER_CLIENT_ID=...
NAVER_CLIENT_SECRET=...

# JWT (32자 이상)
JWT_SECRET=bookcard-secret-key-must-be-at-least-32-characters-long
```

### Backend 실행

```bash
# 개발 환경 (H2, MySQL 불필요)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 운영 환경 (MySQL 필요)
./gradlew bootRun
```

### Frontend 실행

```bash
cd frontend
npm install
npm run dev   # http://localhost:5173
```

### 테스트 실행

```bash
# 단위 테스트 + JaCoCo 커버리지 리포트
./gradlew test

# 커버리지 리포트 열기
open build/reports/jacoco/test/html/index.html
```

<br>

---

## 📡 API 명세

자세한 내용은 [PROJECT_SPECIFICATION.md — API 명세](./PROJECT_SPECIFICATION.md#6-api-명세) 참고

| Method | Endpoint | 인증 | 설명 |
|--------|----------|:----:|------|
| `POST` | `/api/auth/register` | ❌ | 회원가입 |
| `POST` | `/api/auth/login` | ❌ | 로그인 |
| `GET` | `/api/books/paged` | ❌ | 북카드 페이지 조회 (12개/페이지) |
| `GET` | `/api/books/my` | ✅ | 내 북카드 조회 (페이지네이션) |
| `GET` | `/api/books/search` | ❌ | 네이버 도서 검색 |
| `GET` | `/api/books/{id}` | ❌ | 북카드 단건 조회 |
| `POST` | `/api/books/generate` | ✅ | 북카드 생성 (동기) |
| `POST` | `/api/books/generate/stream` | ✅ | 북카드 생성 (SSE) |
| `DELETE` | `/api/books/{id}` | ✅ | 북카드 삭제 (본인만) |
| `POST` | `/api/books/{id}/like` | ✅ | 좋아요 토글 |
| `GET` | `/api/books/{id}/like` | ✅ | 좋아요 상태 조회 |

<br>

---

## 📊 성능

### k6 부하 테스트 — `GET /api/books/paged`

시나리오: 10초 워밍업 → **50명 동시 접속** 20초 유지 → 10초 쿨다운

| 지표 | 결과 |
|------|------|
| 평균 응답시간 | **7.89ms** |
| p95 응답시간 | **12.72ms** ✅ (<500ms) |
| 최대 응답시간 | 28.84ms |
| 에러율 | **0%** ✅ |
| RPS | **22.49 req/s** |
| 총 요청 | 912건 (100% 성공) |

### 북카드 생성 소요시간 (실측)

| 단계 | 소요시간 | 비율 |
|------|---------|:----:|
| 1단계 · 책 분석 (GPT-4o) | 6,460ms | 30% |
| 2단계 · 한글 요약 (GPT-4o) | 1,756ms | 8% |
| 3단계 · 이미지 프롬프트 (GPT-4o) | 1,678ms | 8% |
| 4단계 · **이미지 생성 (Gemini)** | **11,324ms** | **53%** |
| **합계** | **21,218ms** | 100% |

Gemini 이미지 생성이 전체의 53%를 차지하는 명확한 병목입니다.
SSE로 단계별 진행 상황을 실시간 전달해 대기 경험을 개선했습니다.

<br>

---

## 📁 프로젝트 문서

| 문서 | 설명 |
|------|------|
| [PROJECT_SPECIFICATION.md](./PROJECT_SPECIFICATION.md) | 전체 설계 명세 (ERD · API · 기술 결정) |
| [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md) | 개발 순서 및 구현 가이드 |
| [GitHub Wiki](https://github.com/noiskk/bookcard/wiki) | 마이그레이션 배경 · 핵심 기능 · 성능 분석 |

