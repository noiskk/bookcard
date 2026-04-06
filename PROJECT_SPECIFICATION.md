# BookCard 프로젝트 명세서

> 이 문서는 BookCard 프로젝트의 전체 기획·설계 기준입니다.
> 이 문서만 보고 프로젝트를 처음부터 재구현할 수 있도록 작성되었습니다.

## 목차
1. [프로젝트 개요](#1-프로젝트-개요)
2. [기술 스택 및 선택 이유](#2-기술-스택-및-선택-이유)
3. [시스템 아키텍처](#3-시스템-아키텍처)
4. [데이터베이스 설계](#4-데이터베이스-설계)
5. [회원 시스템 및 인증](#5-회원-시스템-및-인증)
6. [API 명세](#6-api-명세)
7. [핵심 기능 상세](#7-핵심-기능-상세)
8. [프론트엔드 구조](#8-프론트엔드-구조)
9. [환경 설정](#9-환경-설정)
10. [실행 방법](#10-실행-방법)
11. [알려진 이슈 및 개선 로드맵](#11-알려진-이슈-및-개선-로드맵)
12. [면접 대비 기술 설명](#12-면접-대비-기술-설명)

---

## 1. 프로젝트 개요

### 1.1 프로젝트명
**BookCard** - AI 기반 북카드 생성 서비스

### 1.2 서비스 설명
사용자가 책을 검색하면 AI가 한국어 요약문(5줄)과 예술적인 커버 이미지를 생성하여
하나의 "북카드"로 저장·공유할 수 있는 서비스.

### 1.3 핵심 가치
- 책의 핵심을 **한국어 5줄 요약**으로 압축 전달
- **Gemini** 기반 아트워크로 책의 분위기를 시각화
- 생성된 북카드를 **라이브러리에 저장·관리·공유**

### 1.4 주요 기능 현황

| 기능 | 설명 | 구현 상태 |
|------|------|:---------:|
| 도서 검색 | 네이버 도서 API 기반 실시간 검색 | ✅ |
| AI 북카드 생성 | GPT-4o 분석 + Gemini 이미지 생성 | ✅ |
| SSE 진행 상황 | 생성 중 실시간 단계별 진행률 표시 | ✅ |
| 라이브러리 | 전체 북카드 조회·검색·삭제 | ✅ |
| 북카드 공유 | URL 기반 공유 링크 생성 | ✅ |
| 회원가입/로그인 | JWT 기반 인증 | ✅ |
| 좋아요 | 북카드 좋아요 토글 (중복 방지) | ✅ |
| Settings | AI 생성 스타일 설정 반영 | ✅ |
| 내 북카드 탭 | 서버 연동 (GET /api/books/my) | ✅ |
| 로그인 상태 표시 | 헤더에 닉네임 + 로그아웃 버튼 | ✅ |
| 비로그인 가드 | 생성 시도 시 로그인 페이지 리다이렉트 | ✅ |
| 토큰 만료 처리 | 401 응답 시 자동 로그아웃 | ✅ |
| 삭제 확인 모달 | 커스텀 모달로 삭제 확인 | ✅ |
| 페이지네이션 | Library 서버 페이지네이션 연동 | ✅ |

---

## 2. 기술 스택 및 선택 이유

### 2.1 Backend

| 기술 | 버전 | 선택 이유 |
|------|------|----------|
| Java | 17 | LTS 버전, Spring Boot 3.x 필수 요구사항 |
| Spring Boot | 3.5.9 | 국내 금융·엔터프라이즈 표준 프레임워크 |
| Spring Data JPA | - | SQL 직접 작성 없이 CRUD 처리, N+1 문제 제어 |
| Spring Security | - | 필터 체인 기반 인증·인가, JWT 통합 용이 |
| Spring AI | 1.0.0-M6 | OpenAI ChatClient 추상화, 프롬프트 체이닝 구현 |
| Google GenAI SDK | 1.2.0 | Gemini 이미지 생성 (Base64 직접 반환) |
| Gradle | 8.14.3 | Maven 대비 빌드 속도 우수, Groovy DSL |
| Lombok | - | 보일러플레이트 코드 제거 |

### 2.2 Database

| 환경 | DB | 이유 |
|------|-----|------|
| 개발·테스트 | H2 In-Memory | 별도 설치 없이 빠른 개발 가능, `--spring.profiles.active=dev` |
| 운영 | MySQL 8.x | ACID 보장, 국내 금융권 표준 RDBMS |

### 2.3 Frontend

| 기술 | 버전 | 선택 이유 |
|------|------|----------|
| React | 19.2.0 | 컴포넌트 기반 UI, 생태계 성숙 |
| Vite | 7.2.4 | CRA 대비 빠른 HMR, 빌드 속도 |
| Tailwind CSS | 4.1.18 | 유틸리티 퍼스트, 빠른 UI 구현 |
| React Router | 7.12.0 | SPA 라우팅 표준 |

### 2.4 인증

| 기술 | 버전 | 선택 이유 |
|------|------|----------|
| jjwt | 0.12.6 | Java JWT 표준 구현체, Spring과 통합 용이 |
| BCrypt | - | 단방향 해시 + 솔트 자동 생성, 레인보우 테이블 방어 |

### 2.5 외부 API

| API | 용도 |
|-----|------|
| Naver Books API | 도서 검색 (제목, 저자, ISBN, 표지 이미지) |
| OpenAI GPT-4o | 책 분석 (장르·분위기·테마·감정), 한국어 요약 생성, 이미지 프롬프트 생성 |
| Google Gemini (`gemini-2.5-flash-image`) | 커버 아트 생성 (Base64 바이너리 직접 반환) |

---

## 3. 시스템 아키텍처

### 3.1 전체 흐름

```
┌─────────────────────────────────┐
│   Browser (React + Vite :5173)  │
└──────────────┬──────────────────┘
               │ HTTP / SSE
               ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot (:8080)                         │
│                                                          │
│  JwtAuthFilter → SecurityFilterChain → Controller        │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │AuthController│  │BookController│  │GlobalException│  │
│  └──────┬───────┘  └──────┬───────┘  │   Handler     │  │
│         │                 │          └───────────────┘  │
│  ┌──────▼───────┐  ┌──────▼───────┐                     │
│  │  AuthService │  │  BookService │                      │
│  └──────────────┘  └──────┬───────┘                     │
│                    ┌──────┴──────────────────────┐       │
│             ┌──────▼──────┐  ┌──────────────┐   │       │
│             │OpenAiService│  │NaverSearch   │   │       │
│             └──────┬──────┘  │Service       │   │       │
│                    │         └──────────────┘   │       │
│             ┌──────▼──────┐  ┌──────────────┐   │       │
│             │ImageStorage │  │BookRepository│   │       │
│             │Service      │  └──────┬───────┘   │       │
│             └─────────────┘         │           │       │
└─────────────────────────────────────┼───────────┘       │
                        ┌─────────────┼────────────────┐  │
                        ▼             ▼                 ▼  │
                   ┌─────────┐  ┌──────────┐  ┌──────────┐│
                   │MySQL/H2 │  │OpenAI API│  │Naver API ││
                   └─────────┘  └──────────┘  └──────────┘│
```

### 3.2 디렉토리 구조

```
bookcard/
├── src/
│   ├── main/
│   │   ├── java/com/example/bookcard/
│   │   │   ├── BookcardApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AiConfig.java               # Spring AI ChatClient Bean 등록
│   │   │   │   ├── AsyncConfig.java            # ExecutorService Bean (SSE용)
│   │   │   │   ├── CorsConfig.java             # CORS 허용 설정
│   │   │   │   ├── GlobalExceptionHandler.java # 전역 예외처리 (@RestControllerAdvice)
│   │   │   │   ├── JwtAuthFilter.java          # JWT 검증 필터 (OncePerRequestFilter)
│   │   │   │   ├── JwtUtil.java                # 토큰 생성/검증 유틸
│   │   │   │   ├── PasswordEncoderConfig.java  # BCrypt Bean (순환참조 방지 분리)
│   │   │   │   ├── SecurityConfig.java         # Security 필터체인 설정
│   │   │   │   └── WebConfig.java              # 정적 리소스 핸들러
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java         # 회원가입, 로그인
│   │   │   │   └── BookController.java         # 북카드 CRUD + SSE
│   │   │   ├── dto/
│   │   │   │   ├── auth/
│   │   │   │   │   ├── AuthResponse.java       # { token, email, nickname }
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   └── RegisterRequest.java
│   │   │   │   ├── BookAnalysis.java           # GPT 분석 결과
│   │   │   │   ├── BookGenerateRequest.java
│   │   │   │   ├── BookSearchResult.java
│   │   │   │   ├── GenerationProgress.java     # SSE 이벤트 DTO
│   │   │   │   ├── NaverBookItem.java
│   │   │   │   ├── NaverSearchResponse.java
│   │   │   │   └── RecommendationCategory.java
│   │   │   ├── entity/
│   │   │   │   ├── Book.java                   # @ManyToOne User creator
│   │   │   │   └── User.java                   # UserDetails 구현체
│   │   │   ├── repository/
│   │   │   │   ├── BookRepository.java         # + Page<Book> 페이지네이션
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       ├── AuthService.java            # UserDetailsService 구현
│   │   │       ├── BookService.java
│   │   │       ├── ImageStorageService.java
│   │   │       ├── NaverSearchService.java
│   │   │       └── OpenAiService.java          # 프롬프트 체이닝 4단계
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/example/bookcard/
│           ├── BookcardApplicationTests.java   # @ActiveProfiles("dev")
│           ├── config/
│           │   └── JwtUtilTest.java            # 5개 테스트
│           └── service/
│               ├── AuthServiceTest.java        # 3개 테스트
│               └── BookServiceTest.java        # 8개 테스트
├── frontend/
│   ├── src/
│   │   ├── App.jsx                             # 라우터 설정
│   │   ├── main.jsx
│   │   ├── api/
│   │   │   ├── authApi.js                      # login, register, logout, getToken
│   │   │   └── bookApi.js                      # getAuthHeader() + 인증 헤더 자동 추가
│   │   ├── components/
│   │   │   ├── BookCard.jsx
│   │   │   ├── BookViewer.jsx                  # 풀스크린, 키보드 네비, Ken Burns
│   │   │   ├── GenerateModal.jsx
│   │   │   ├── Layout.jsx                      # 닉네임 표시 + 로그아웃
│   │   │   └── SearchBar.jsx
│   │   ├── pages/
│   │   │   ├── BookShare.jsx
│   │   │   ├── Library.jsx                     # 서버 페이지네이션 + 삭제 모달
│   │   │   ├── Login.jsx
│   │   │   ├── Main.jsx                        # 비로그인 가드
│   │   │   ├── Register.jsx
│   │   │   └── Settings.jsx                    # AI 생성에 반영됨
│   │   └── utils/
│   │       └── myBooks.js
│   ├── package.json
│   └── vite.config.js
├── uploads/
│   └── images/                                 # Gemini 생성 이미지 로컬 저장
├── load-test.js                                # k6 부하 테스트 스크립트
├── CLAUDE.md
├── DEVELOPMENT_GUIDE.md
├── PROJECT_SPECIFICATION.md
├── build.gradle
└── .env                                        # 환경 변수 (git 제외)
```

### 3.3 레이어 책임

| 레이어 | 클래스 | 책임 |
|--------|--------|------|
| Filter | JwtAuthFilter | 요청마다 JWT 검증, SecurityContext 인증 정보 저장 |
| Controller | AuthController, BookController | HTTP 요청/응답 처리, 입력값 검증 위임 |
| Service | AuthService, BookService, ... | 비즈니스 로직, 트랜잭션 경계 |
| Repository | BookRepository, UserRepository | JPA 기반 DB 접근 |
| Entity | Book, User | DB 테이블 매핑, 도메인 로직 |
| Config | SecurityConfig, AiConfig, GlobalExceptionHandler, ... | 횡단 관심사 |

---

## 4. 데이터베이스 설계

### 4.1 ERD

```
┌──────────────────────────────────┐
│             users                │
├──────────────────────────────────┤
│ id           BIGINT  PK, AI      │
│ email        VARCHAR  NOT NULL   │  ← UNIQUE
│ password     VARCHAR  NOT NULL   │  ← BCrypt 해시
│ nickname     VARCHAR  NOT NULL   │
│ created_at   DATETIME            │
└──────────────────┬───────────────┘
                   │ 1
                   │
                   │ N
┌──────────────────▼───────────────┐
│              books               │
├──────────────────────────────────┤
│ id             BIGINT  PK, AI    │
│ isbn           VARCHAR           │
│ title          VARCHAR  NOT NULL │
│ author         VARCHAR  NOT NULL │
│ publisher      VARCHAR           │
│ original_image VARCHAR(1000)     │  ← 네이버 제공 표지 URL
│ generated_image VARCHAR(1000)    │  ← Gemini 이미지 로컬 경로
│ description    TEXT              │
│ like_count     INT  DEFAULT 0    │
│ created_at     DATETIME          │
│ user_id        BIGINT  FK        │  → users.id (북카드 생성자)
└──────────────────┬───────────────┘
                   │ 1
                   │
                   │ N
┌──────────────────▼───────────────┐
│          book_summaries          │
├──────────────────────────────────┤
│ book_id      BIGINT  FK          │  → books.id
│ summary_line VARCHAR(1000)       │  ← AI 생성 요약 1문장
│ line_order   INT                 │  ← 순서 (0~4)
└──────────────────────────────────┘
```

### 4.2 주요 설계 결정

**summary를 별도 테이블로 분리한 이유**
`@ElementCollection` + `@OrderColumn`으로 순서가 보장된 문자열 목록을 저장.
Book 엔티티 내에 `List<String>`으로 선언하면 JPA가 자동으로 조인 테이블 생성.

**generated_image를 URL이 아닌 로컬 경로로 저장하는 이유**
Gemini는 이미지를 Base64 바이너리로 직접 반환한다. 외부 URL이 없으므로
`ImageStorageService`가 생성 즉시 로컬(`uploads/images/`)에 파일로 저장하고
`/images/UUID.png` 경로를 DB에 저장한다. 서버 재시작 후에도 이미지가 유지된다.

**user_id FK가 nullable인 이유**
초기에 비회원 생성을 허용하다가 JWT 인증 추가 이후 creator 필드를 붙였기 때문.
기존 데이터와의 호환성을 위해 nullable 유지.

---

## 5. 회원 시스템 및 인증

### 5.1 기능별 접근 권한

| 기능 | 비회원 | 회원 |
|------|:------:|:----:|
| 북카드 목록/상세 조회 | ✅ | ✅ |
| 도서 검색 (네이버) | ✅ | ✅ |
| 공유 링크 조회 | ✅ | ✅ |
| 북카드 생성 | ❌ | ✅ |
| 북카드 삭제 (본인) | ❌ | ✅ |
| 좋아요 | ❌ | ✅ |

### 5.2 JWT 인증 흐름

```
[회원가입/로그인]
Client → POST /api/auth/register or /login
       → AuthController → AuthService → UserRepository
       → BCrypt 검증 → JwtUtil.generateToken()
       → 응답: { token, email, nickname }
       → Client: localStorage.setItem('bookcard_token', token)

[인증 필요 API 요청]
Client → Authorization: Bearer {token}
       → JwtAuthFilter.doFilterInternal()
         → JwtUtil.extractEmail(token)
         → UserDetailsService.loadUserByUsername(email)
         → JwtUtil.isValid(token, userDetails)
         → SecurityContextHolder에 인증 정보 저장
       → Controller 정상 처리
```

### 5.3 SecurityConfig 엔드포인트 규칙

```
공개 (permitAll):
  - /api/auth/**
  - GET /api/books/**
  - /images/**
  - /h2-console/**

인증 필요 (authenticated):
  - POST /api/books/generate
  - POST /api/books/generate/stream
  - DELETE /api/books/**
  - POST /api/books/*/like
```

### 5.4 순환 참조 해결

**문제**: SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig

**해결**: `PasswordEncoderConfig.java`를 별도 `@Configuration`으로 분리
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
SecurityConfig는 PasswordEncoderConfig의 Bean을 주입받아 사용.

---

## 6. API 명세

### 6.1 인증 API

#### POST /api/auth/register — 회원가입
**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "사용자닉네임"
}
```
**Validation**: email `@Email` 필수, password `@Size(min=6)` 필수, nickname `@Size(min=2, max=20)` 필수

**Response 200**
```json
{
  "token": "eyJhbGciOiJIUzM4NC...",
  "email": "user@example.com",
  "nickname": "사용자닉네임"
}
```
**Response 400** — 중복 이메일 또는 유효성 검증 실패

#### POST /api/auth/login — 로그인
**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```
**Response 200** — 회원가입과 동일 구조
**Response 401** — 이메일/비밀번호 불일치

### 6.2 북카드 API

#### GET /api/books — 전체 목록 조회
**Response 200**
```json
[
  {
    "id": 1,
    "isbn": "9788936434267",
    "title": "채식주의자",
    "author": "한강",
    "publisher": "창비",
    "originalImage": "https://...",
    "generatedImage": "/images/abc123.png",
    "description": "...",
    "summary": ["문장1", "문장2", "문장3", "문장4", "문장5"],
    "likeCount": 42,
    "createdAt": "2026-04-04T10:00:00"
  }
]
```

#### GET /api/books/paged — 페이지네이션 조회
**Query Params**: `page=0` (기본값), `size=12` (기본값)
**Response 200** — Spring Page 객체 (content, totalPages, totalElements 포함)

#### GET /api/books/{id} — 단건 조회
**Response 404** — 존재하지 않는 ID

#### GET /api/books/search — 네이버 도서 검색
**Query Params**: `query=채식주의자`, `start=1` (기본값)
**Response 200**
```json
[
  {
    "title": "채식주의자",
    "author": "한강",
    "publisher": "창비",
    "image": "https://...",
    "isbn": "9788936434267",
    "description": "..."
  }
]
```

#### GET /api/books/library/search — 라이브러리 내 검색
**Query Params**: `q=한강`
제목 또는 저자 기준 LIKE 검색

#### POST /api/books/generate — 북카드 생성 (동기)
**인증 필요** (Authorization: Bearer {token})
**Request Body**
```json
{
  "isbn": "9788936434267",
  "title": "채식주의자",
  "author": "한강",
  "publisher": "창비",
  "originalImage": "https://...",
  "description": "..."
}
```
**Response 200** — 생성된 Book 객체
**Response 400** — ISBN 중복 시 `{ "message": "이미 생성된 북카드가 있습니다 (ID: 1)" }`

#### POST /api/books/generate/stream — 북카드 생성 (SSE)
**인증 필요** | **Content-Type**: text/event-stream
**이벤트 흐름**
```
event: progress
data: {"step":1,"status":"in_progress","message":"책의 장르와 분위기를 분석하고 있습니다..."}

event: progress
data: {"step":2,...,"message":"감성적인 한글 요약을 작성하고 있습니다..."}

event: progress
data: {"step":3,...,"message":"예술적인 이미지 컨셉을 구상하고 있습니다..."}

event: progress
data: {"step":4,...,"message":"Gemini로 커버 이미지를 생성하고 있습니다..."}

event: complete
data: {"step":4,"status":"completed","message":"완료!","data":{/* Book 객체 */}}
```
**타임아웃**: 5분 (300,000ms)

#### DELETE /api/books/{id} — 북카드 삭제
**인증 필요**
**Response 204** — 성공
**Response 403** — 본인이 생성하지 않은 북카드 `{ "message": "본인이 생성한 북카드만 삭제할 수 있습니다" }`
**Response 400** — 존재하지 않는 ID

#### POST /api/books/{id}/like — 좋아요 토글
**인증 필요**
**Response 200** — `{ "liked": true/false, "likeCount": N }` (BookLike 엔티티 기반 토글)

### 6.3 공통 에러 응답

| HTTP Status | 발생 상황 |
|------------|---------|
| 400 | 유효성 검증 실패, 중복 ISBN, 잘못된 요청 |
| 401 | 인증 실패 (토큰 없음, 만료, 불일치) |
| 403 | 인가 실패 (타인 북카드 삭제 등) |
| 500 | 서버 내부 오류 (AI API 실패 포함) |

---

## 7. 핵심 기능 상세

### 7.1 AI 프롬프트 체이닝 파이프라인

단일 프롬프트 대비 **품질 향상**을 위해 4단계 체이닝 적용.
각 단계의 출력이 다음 단계의 입력이 됨.

```
입력: { title, author, description }
        │
        ▼
[Step 1] 책 분석 (GPT-4o)  — 실측 약 6,400ms
  → BookAnalysis { genre, mood, themes[], emotions[], visualStyle, colors[], era, setting,
                   bookCategory, knowledgeLevel, uniqueHighlight }
        │
        ▼
[Step 2] 한국어 요약 생성 (GPT-4o)  — 실측 약 1,750ms
  입력: 책 정보 + Step1 분석 결과
  → List<String> summary (5문장)
        │
        ▼
[Step 3] 이미지 프롬프트 생성 (GPT-4o)  — 실측 약 1,680ms
  입력: 책 정보 + Step1 분석 + Step2 요약
  → String imagePrompt (영어, Gemini용, 200단어 이하)
        │
        ▼
[Step 4] 이미지 생성 (Gemini)  — 실측 약 11,300ms
  입력: Step3 imagePrompt
  → byte[] imageData (Base64 디코딩된 바이너리)
        │
        ▼
[저장] ImageStorageService
  → uploads/images/UUID.png 로 저장
  → /images/UUID.png 경로를 DB에 저장
        │
        ▼
출력: Book 엔티티 저장 및 반환  (전체 소요 약 21초)
```

**GPT-4o 호출 설정** (Spring AI)
- model: `gpt-4o`
- maxTokens: 1000
- temperature: 0.7

**Gemini 호출 설정** (Google GenAI SDK)
- model: `gemini-2.5-flash-image`
- 출력: Base64 인라인 데이터 (URL 아님)

### 7.2 SSE (Server-Sent Events) 실시간 스트리밍

**선택 이유**: 북카드 생성은 약 21초 소요. 단순 로딩보다 단계별 진행 표시로 UX 개선.
WebSocket이 아닌 SSE를 선택한 이유: 서버→클라이언트 단방향 통신으로 충분하며, SSE는 HTTP 기반이라 방화벽 친화적.

**구현 방식**
```java
// AsyncConfig — Bean으로 관리 (컨트롤러 직접 생성 방지)
@Bean(destroyMethod = "shutdown")
public ExecutorService sseExecutorService() {
    return Executors.newCachedThreadPool();
}

// BookController
SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃

// SecurityContext를 스레드에 전달 (비동기 스레드에서도 인증 유지)
SecurityContext securityContext = SecurityContextHolder.getContext();
executor.execute(() -> {
    SecurityContextHolder.setContext(securityContext);
    Book book = bookService.generateBook(request, (progress) -> {
        emitter.send(SseEmitter.event().name("progress").data(progress));
    });
    emitter.send(SseEmitter.event().name("complete").data(book));
    emitter.complete();
});
```

**핵심 포인트**: `SecurityContextHolder`는 기본적으로 `ThreadLocal` 기반이라
비동기 스레드에서 인증 정보가 사라진다.
`DispatcherType.ASYNC`를 Security 필터에 허용하고 컨텍스트를 명시적으로 전달해야 한다.

### 7.3 전역 예외 처리

`GlobalExceptionHandler` (`@RestControllerAdvice`)가 모든 예외를 JSON으로 통일.

| 예외 | HTTP | 응답 |
|------|------|------|
| MethodArgumentNotValidException | 400 | `{ "fieldName": "에러메시지" }` |
| IllegalArgumentException | 400 | `{ "message": "..." }` |
| AccessDeniedException | 403 | `{ "message": "..." }` |
| BadCredentialsException | 401 | `{ "message": "이메일 또는 비밀번호가 올바르지 않습니다" }` |
| Exception (기타) | 500 | `{ "message": "서버 오류가 발생했습니다" }` |

---

## 8. 프론트엔드 구조

### 8.1 라우팅 구조

```
/           → Main.jsx       (검색 + 북카드 생성)
/library    → Library.jsx    (라이브러리 조회/관리)
/settings   → Settings.jsx   (AI 생성 설정)
/book/:id   → BookShare.jsx  (공유 링크 조회)
/login      → Login.jsx      (로그인)
/register   → Register.jsx   (회원가입)
```

레이아웃: `/login`, `/register`는 Layout 없이 standalone. 나머지는 Layout.jsx 래핑.

### 8.2 API 클라이언트 구조

**authApi.js** — 인증 관련
```javascript
login(email, password)    // POST /api/auth/login → 토큰 localStorage 저장
register(...)             // POST /api/auth/register
logout()                  // localStorage 토큰 제거
getToken()                // localStorage에서 토큰 반환
isLoggedIn()              // 토큰 존재 여부
```

**bookApi.js** — 북카드 관련
```javascript
// 공통 헬퍼
getAuthHeader()           // { Authorization: 'Bearer {token}' } 반환

// 공개 API (헤더 불필요)
getAllBooks()
getBookById(id)
searchBooks(query, start)
searchLibrary(query)

// 인증 필요 API (getAuthHeader() 자동 포함)
generateBook(bookData)
generateBookWithProgress(bookData, onProgress, onComplete, onError)
deleteBook(id)
likeBook(id)
```

### 8.3 주요 컴포넌트

**BookViewer.jsx** — 가장 핵심적인 UI 컴포넌트
- 풀스크린 오버레이 뷰어
- 3페이지 구성: 표지 → 요약 5줄 → 생성 이미지
- 키보드 네비게이션 (←→, Space, Esc)
- 마우스 좌/우측 클릭으로 페이지 전환
- Ken Burns 이미지 줌 애니메이션
- 상단 진행 바 (페이지 프로그레스)
- 좋아요, 공유(URL 복사) 버튼

**GenerateModal.jsx** — 생성 UI
- SSE 연결 및 진행률 표시
- 단계별 메시지 표시

### 8.4 인증 상태 관리

AuthContext 대신 `authApi` 유틸리티 + localStorage 방식으로 구현.
- 로그인/회원가입 시 JWT 토큰과 닉네임을 localStorage에 저장
- `Layout.jsx`에서 닉네임 표시, 로그아웃 시 localStorage 클리어
- `bookApi.js`에서 401 응답 인터셉트 → 자동 로그아웃 + `/login` 리다이렉트
- `Main.jsx`에서 비로그인 생성 시도 시 `/login` 리다이렉트

---

## 9. 환경 설정

### 9.1 .env 파일

```
# Database (prod 프로파일)
DB_USERNAME=root
DB_PASSWORD=yourpassword

# Naver Open API
NAVER_CLIENT_ID=발급받은_클라이언트_ID
NAVER_CLIENT_SECRET=발급받은_시크릿

# OpenAI
OPENAI_API_KEY=sk-...

# Google Gemini
GEMINI_API_KEY=AIza...

# JWT (32자 이상)
JWT_SECRET=bookcard-secret-key-must-be-at-least-32-characters-long
```

### 9.2 application.yml 구조

```yaml
spring:
  profiles:
    active: prod    # 기본 prod, 테스트 시 dev
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
          model: gpt-4o
          temperature: 0.7
          max-tokens: 1000

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 86400000   # 24시간

naver.client:
  id: ${NAVER_CLIENT_ID}
  secret: ${NAVER_CLIENT_SECRET}

gemini:
  api.key: ${GEMINI_API_KEY}
  image.model: gemini-2.5-flash-image

---
spring.config.activate.on-profile: dev
  datasource:
    url: jdbc:h2:mem:bookcard
    driver-class-name: org.h2.Driver

---
spring.config.activate.on-profile: prod
  datasource:
    url: jdbc:mysql://localhost:3306/bookcard
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 9.3 build.gradle 의존성

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // AI
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M6'
    implementation 'com.google.genai:google-genai:1.2.0'

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // 기타
    implementation 'me.paulschwarz:spring-dotenv:4.0.0'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Database
    runtimeOnly 'com.mysql:mysql-connector-j'
    runtimeOnly 'com.h2database:h2'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

---

## 10. 실행 방법

### 10.1 Backend 실행

```bash
# 1. 환경 변수 설정
cp .env.example .env
# .env 파일에 API 키 입력

# 2. dev 프로파일로 실행 (H2, MySQL 불필요)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. prod 프로파일로 실행 (MySQL 필요, 기본값)
./gradlew bootRun

# 4. 테스트 실행
./gradlew test

# 5. 부하 테스트 (k6 필요)
k6 run load-test.js
```

### 10.2 Frontend 실행

```bash
cd frontend
npm install
npm run dev    # http://localhost:5173
```

### 10.3 개발 환경 확인 체크리스트

- [ ] `http://localhost:8080/api/books` — 빈 배열 반환 확인
- [ ] `http://localhost:8080/h2-console` — H2 콘솔 접근 (dev 프로파일)
- [ ] `http://localhost:5173` — 프론트엔드 접근
- [ ] POST `/api/auth/register` — 회원가입 후 토큰 반환 확인
- [ ] POST `/api/books/generate/stream` — SSE 이벤트 수신 확인

---

## 11. 알려진 이슈 및 개선 로드맵

### 🔴 우선순위 높음 (면접 지적 가능성 높음) — ✅ 전체 완료

| # | 이슈 | 개선 내용 | 상태 |
|---|------|----------|:----:|
| 1 | Settings AI 미반영 (데드 UI) | generate 요청에 설정값 포함, 백엔드 프롬프트 커스터마이징 | ✅ |
| 2 | 좋아요 중복 방지 없음 | `BookLike` 엔티티 + 토글 방식, likeCount 실제 카운트 동기화 | ✅ |
| 3 | 내 북카드 탭 localStorage 기반 | `GET /api/books/my` 서버 연동, 페이지네이션 | ✅ |

### 🟡 우선순위 중간 (UX 완성도) — ✅ 전체 완료

| # | 이슈 | 개선 내용 | 상태 |
|---|------|----------|:----:|
| 4 | 비로그인 생성 시도 시 401 에러 | `Main.jsx`에서 `isLoggedIn()` 체크 → `/login` 리다이렉트 | ✅ |
| 5 | 헤더 로그인 상태 표시 없음 | `Layout.jsx`에 닉네임 표시 (localStorage 저장), 로그아웃 버튼 | ✅ |
| 6 | 토큰 만료 처리 없음 | `bookApi.js`에 401 인터셉터 → 자동 로그아웃 + `/login` 이동 | ✅ |
| 7 | 삭제 확인 모달 없음 | `Library.jsx`에 커스텀 삭제 확인 모달 (책 제목 표시, 취소/삭제) | ✅ |
| 8 | Library 전체 조회 (페이징 없음) | `getPagedBooks()` 연동, 12개씩 서버 페이지네이션 + 페이지 버튼 UI | ✅ |

### 🔵 기술 부채 — ✅ 전체 완료

| # | 이슈 | 개선 내용 | 상태 |
|---|------|----------|:----:|
| 9 | 네이버 검색 결과 캐싱 없음 | Caffeine `@Cacheable` (30분 TTL, 최대 200항목), `CacheConfig` 추가 | ✅ |
| 10 | 동시 생성 시 같은 ISBN 중복 생성 가능 | `ConcurrentHashMap` ISBN 락 + SSE `DataIntegrityViolationException` → 기존 북카드 ID 반환 | ✅ |

---

## 12. 면접 대비 기술 설명

### Q. Spring Security + JWT를 선택한 이유는?
> 세션 방식은 서버 메모리에 상태를 저장해 수평 확장 시 세션 공유 문제가 발생합니다.
> JWT는 Stateless라 서버가 상태를 기억하지 않아 확장성이 좋고,
> Signature 검증만으로 인증이 완결됩니다.
> Spring Security의 FilterChain에 JwtAuthFilter를 끼워 넣으면
> 모든 요청에 일관된 인증 로직을 적용할 수 있습니다.

### Q. SSE를 선택한 이유는? WebSocket은?
> AI 이미지 생성은 서버→클라이언트 단방향 이벤트 스트림입니다.
> WebSocket은 양방향 통신이 필요할 때 적합하지만, 이 경우 오버엔지니어링입니다.
> SSE는 일반 HTTP 위에서 동작해 방화벽 친화적이고, 브라우저 기본 지원이 좋습니다.

### Q. 프롬프트 체이닝을 구현한 이유는?
> 단일 프롬프트로 분석+요약+이미지프롬프트를 한 번에 요청하면 품질이 낮아집니다.
> 1단계 분석 결과를 2단계 요약에, 그 결과를 3단계 이미지프롬프트에 전달하면
> 각 단계가 풍부한 컨텍스트를 갖게 되어 최종 결과물의 품질이 향상됩니다.

### Q. 순환 참조 문제를 어떻게 해결했나요?
> SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig
> 구조에서 순환이 발생했습니다.
> PasswordEncoder를 별도 PasswordEncoderConfig 클래스로 분리함으로써
> 의존 사이클을 끊었습니다.
> @Lazy 어노테이션으로도 해결할 수 있지만, Bean 책임을 명확히 분리하는 것이
> 더 명시적이고 테스트하기 쉬운 구조라 이 방법을 선택했습니다.

### Q. @ElementCollection vs 별도 엔티티, 어떤 기준으로 선택했나요?
> 요약 문장은 Book 없이 독립적으로 존재할 수 없고,
> 조회 시 항상 Book과 함께 조회됩니다.
> 이런 종속적 값 타입 컬렉션에 @ElementCollection이 적합합니다.
> 요약 문장 자체에 비즈니스 로직이 생기거나 독립 조회가 필요하다면 엔티티로 승격할 것입니다.

### Q. Gemini 이미지를 왜 로컬에 저장하나요?
> Gemini는 이미지를 URL이 아닌 Base64 바이너리로 직접 반환합니다.
> 외부에서 접근 가능한 URL 자체가 없으므로 서버에 파일로 저장하는 것이 유일한 선택입니다.
> ImageStorageService가 Base64 데이터를 디코딩해 uploads/images/에 저장하고,
> 정적 리소스 핸들러(WebConfig)를 통해 /images/** 경로로 서빙합니다.

### Q. SSE 비동기 처리에서 SecurityContext는 어떻게 유지했나요?
> SecurityContextHolder는 기본적으로 ThreadLocal 기반이라
> executor 스레드에서는 인증 정보가 사라집니다.
> 컨트롤러에서 현재 SecurityContext를 캡처한 뒤,
> executor.execute() 내부에서 SecurityContextHolder.setContext()로 명시적으로 설정했습니다.
> 처리 완료 후에는 clearContext()로 스레드 오염을 방지합니다.
