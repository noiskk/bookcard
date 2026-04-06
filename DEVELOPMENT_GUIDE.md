# BookCard 개발 가이드

이 문서만 보고 프로젝트를 처음부터 다시 만들 수 있도록 작성됐습니다.
실제 구현 과정에서 발생한 오류와 해결책도 포함합니다.

---

## 개발 순서 요약

```
Phase 1: 프로젝트 초기 설정 (Spring Boot + 의존성 + 설정파일)
Phase 2: Entity 설계 (User → Book 순서 필수)
Phase 3: Repository 구현
Phase 4: 설정 클래스 구현 (Security 먼저! 순환참조 주의)
Phase 5: DTO 생성
Phase 6: Service 구현
Phase 7: Controller 구현
Phase 8: 외부 API 연동 (Naver, OpenAI)
Phase 9: 예외처리 및 유틸리티
Phase 10: 테스트 코드
Phase 11: Frontend 개발
```

---

## Phase 1: 프로젝트 초기 설정

### 1.1 Spring Boot 프로젝트 생성

**Spring Initializr** (https://start.spring.io) 설정:

| 항목 | 값 |
|------|-----|
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 3.x.x 최신 안정 버전 |
| Group | com.example |
| Artifact | bookcard |
| Packaging | Jar |
| Java | 17 |

**Initializr에서 선택할 Dependencies:**
- Spring Web
- Spring Data JPA
- Spring Security
- MySQL Driver
- H2 Database
- Lombok
- Validation

### 1.2 build.gradle 전체

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.4'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // JPA
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Security
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // Validation (@Valid, @NotBlank 등)
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // 환경변수 (.env 파일 지원)
    implementation 'me.paulschwarz:spring-dotenv:4.0.0'

    // JWT (주의: 0.12.6 사용 - 이전 버전과 API 다름)
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

    // Lombok
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

tasks.named('test') {
    useJUnitPlatform()
}
```

### 1.3 application.yml

```yaml
# 기본 설정 (모든 프로필 공통)
spring:
  profiles:
    active: dev   # 로컬 개발 시 dev 프로필 사용

  jpa:
    hibernate:
      ddl-auto: update    # 테이블 자동 생성/수정
    show-sql: true
    properties:
      hibernate:
        format_sql: true
    open-in-view: false   # OSIV 비활성화 (권장)

server:
  port: 8080

# JWT 설정
jwt:
  secret: ${JWT_SECRET}         # .env에서 로드
  expiration-ms: 86400000       # 24시간 (ms)

# Naver API
naver:
  client:
    id: ${NAVER_CLIENT_ID}
    secret: ${NAVER_CLIENT_SECRET}

# OpenAI
openai:
  api:
    key: ${OPENAI_API_KEY}

---
# dev 프로필 (H2 인메모리 DB)
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:h2:mem:bookcard
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect

---
# prod 프로필 (MySQL)
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: jdbc:mysql://localhost:3306/bookcard?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
```

### 1.4 .env 파일 (루트 디렉토리)

```
DB_USERNAME=root
DB_PASSWORD=yourpassword
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret
OPENAI_API_KEY=sk-...
JWT_SECRET=your-very-long-and-secure-256-bit-secret-key-here
```

> `.env`는 `.gitignore`에 추가할 것.

### 1.5 패키지 폴더 생성

```
src/main/java/com/example/bookcard/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   └── auth/
└── config/
```

**확인:**
- [ ] `./gradlew bootRun` 서버 실행 성공
- [ ] http://localhost:8080/h2-console 접속 가능

---

## Phase 2: Entity 설계

> **순서 필수:** User → Book. Book이 User를 FK로 참조하므로 User를 먼저 만들어야 합니다.

### 2.1 User Entity

`entity/User.java`

```java
package com.example.bookcard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // UserDetails 구현 - Spring Security 필수
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return email;  // 이메일을 username으로 사용
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
```

### 2.2 Book Entity

`entity/Book.java`

```java
package com.example.bookcard.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "books")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String publisher;

    @Column(length = 1000)
    private String originalImage;

    @Column(length = 1000)
    private String generatedImage;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "book_summaries",
                     joinColumns = @JoinColumn(name = "book_id"))
    @OrderColumn(name = "summary_order")
    @Column(name = "summary")
    @Builder.Default
    private List<String> summary = new ArrayList<>();

    @Builder.Default
    private Integer likeCount = 0;

    // 북카드 생성자 (작성자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore   // 순환참조 방지 (JSON 직렬화 시 User 제외)
    private User creator;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // 표지 이미지 (생성된 이미지 우선, 없으면 원본)
    public String getCoverImage() {
        return generatedImage != null ? generatedImage : originalImage;
    }
}
```

> **주의:** `@Builder.Default`를 `summary`와 `likeCount`에 붙이지 않으면 Builder 사용 시 null이 됩니다.

**확인:**
- [ ] 서버 재시작 후 H2 콘솔에서 `users`, `books`, `book_summaries` 테이블 생성 확인

---

## Phase 3: Repository 구현

`repository/UserRepository.java`

```java
package com.example.bookcard.repository;

import com.example.bookcard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`repository/BookRepository.java`

```java
package com.example.bookcard.repository;

import com.example.bookcard.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findAllByOrderByCreatedAtDesc();

    Page<Book> findAllByOrderByCreatedAtDesc(Pageable pageable);  // 페이지네이션

    Optional<Book> findByIsbn(String isbn);  // ISBN 중복 체크용
}
```

---

## Phase 4: 설정 클래스 구현 (Security)

> **순서가 중요합니다!** Security 설정을 Service보다 먼저 만들어야 합니다.
> 그리고 **순환참조(Circular Dependency)** 를 피하기 위해 PasswordEncoder를 별도 클래스로 분리합니다.

### 순환참조 문제 이해

잘못 설계하면 다음 의존성 사이클이 발생합니다:

```
SecurityConfig → JwtAuthFilter → AuthService → PasswordEncoder → SecurityConfig
```

**해결책: PasswordEncoder를 별도 `@Configuration`으로 분리**

### 4.1 PasswordEncoderConfig (별도 분리 필수!)

`config/PasswordEncoderConfig.java`

```java
package com.example.bookcard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 4.2 JwtUtil

`config/JwtUtil.java`

```java
package com.example.bookcard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(String email) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = parseClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

### 4.3 JwtAuthFilter

`config/JwtAuthFilter.java`

```java
package com.example.bookcard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Bearer 토큰이 없으면 통과
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String email = jwtUtil.extractEmail(jwt);

        // 토큰이 유효하고 아직 인증되지 않은 경우
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtUtil.isValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

### 4.4 SecurityConfig

`config/SecurityConfig.java`

```java
package com.example.bookcard.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;  // PasswordEncoderConfig에서 주입

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 허용
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/books", "/api/books/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        // 인증 필요
                        .requestMatchers(HttpMethod.POST, "/api/books/generate", "/api/books/generate/stream").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/books/*/like").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 4.5 AsyncConfig (SSE용 ExecutorService)

`config/AsyncConfig.java`

```java
package com.example.bookcard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    // destroyMethod = "shutdown" → 앱 종료 시 스레드풀 정상 종료
    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
```

> **왜 별도 Bean으로?** Controller에서 `Executors.newCachedThreadPool()` 직접 생성하면 앱 종료 시 스레드가 정상 종료되지 않습니다 (리소스 누수). Bean으로 관리하면 `destroyMethod`로 자동 종료됩니다.

**확인:**
- [ ] 서버 실행 성공
- [ ] `GET /api/books` → 200 OK (permitAll)
- [ ] `POST /api/books/generate` → 401 Unauthorized (인증 필요)

---

## Phase 5: DTO 생성

### 5.1 인증 관련 DTO

`dto/auth/RegisterRequest.java`

```java
package com.example.bookcard.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다") String password,
    @NotBlank String nickname
) {}
```

`dto/auth/LoginRequest.java`

```java
package com.example.bookcard.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) {}
```

`dto/auth/AuthResponse.java`

```java
package com.example.bookcard.dto.auth;

public record AuthResponse(
    String token,
    String email,
    String nickname
) {}
```

### 5.2 Book 관련 DTO

`dto/BookGenerateRequest.java`

```java
package com.example.bookcard.dto;

import jakarta.validation.constraints.NotBlank;

public record BookGenerateRequest(
    String isbn,
    @NotBlank String title,
    @NotBlank String author,
    String publisher,
    String originalImage,
    String description
) {}
```

---

## Phase 6: Service 구현

### 6.1 AuthService

> **중요:** `UserDetailsService`를 구현합니다. `AuthenticationManager`는 순환참조를 피하기 위해 Controller에 주입합니다.

`service/AuthService.java`

```java
package com.example.bookcard.service;

import com.example.bookcard.config.JwtUtil;
import com.example.bookcard.dto.auth.AuthResponse;
import com.example.bookcard.dto.auth.RegisterRequest;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Spring Security가 호출하는 메서드 (이메일로 유저 조회)
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getNickname());
    }

    public AuthResponse login(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getNickname());
    }
}
```

### 6.2 BookService

`service/BookService.java`

```java
package com.example.bookcard.service;

import com.example.bookcard.dto.BookGenerateRequest;
import com.example.bookcard.entity.Book;
import com.example.bookcard.entity.User;
import com.example.bookcard.repository.BookRepository;
import com.example.bookcard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;

    public List<Book> getAllBooks() {
        return bookRepository.findAllByOrderByCreatedAtDesc();
    }

    public Page<Book> getPagedBooks(Pageable pageable) {
        return bookRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public Book generateBook(BookGenerateRequest request, java.util.function.Consumer<String> progressCallback) {
        // ISBN 중복 체크 (ISBN이 있는 경우만)
        if (request.isbn() != null && !request.isbn().isBlank()) {
            bookRepository.findByIsbn(request.isbn()).ifPresent(existing -> {
                throw new IllegalArgumentException("이미 생성된 북카드가 있습니다 (ID: " + existing.getId() + ")");
            });
        }

        // OpenAI로 북카드 생성 (Prompt Chaining)
        Book book = openAiService.generateWithChaining(request, progressCallback);

        // 생성자(creator) 설정
        book.setCreator(getCurrentUser());

        return bookRepository.save(book);
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("북카드를 찾을 수 없습니다: " + id));

        User currentUser = getCurrentUser();

        // 본인 북카드만 삭제 가능
        if (book.getCreator() == null || !book.getCreator().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("본인의 북카드만 삭제할 수 있습니다.");
        }

        bookRepository.delete(book);
    }

    @Transactional
    public Book likeBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("북카드를 찾을 수 없습니다: " + id));
        book.setLikeCount(book.getLikeCount() + 1);
        return bookRepository.save(book);
    }

    // SecurityContextHolder에서 현재 로그인한 User 반환
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
```

---

## Phase 7: Controller 구현

### 7.1 AuthController

`controller/AuthController.java`

```java
package com.example.bookcard.controller;

import com.example.bookcard.dto.auth.AuthResponse;
import com.example.bookcard.dto.auth.LoginRequest;
import com.example.bookcard.dto.auth.RegisterRequest;
import com.example.bookcard.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;  // AuthService가 아닌 여기에 주입!

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // AuthenticationManager로 비밀번호 검증
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        return ResponseEntity.ok(authService.login(request.email()));
    }
}
```

> **왜 AuthenticationManager를 Service가 아닌 Controller에?**
> AuthService가 AuthenticationManager를 주입받으면 순환참조가 발생합니다.
> AuthenticationManager → AuthService → PasswordEncoder → SecurityConfig → AuthenticationManager

### 7.2 BookController

`controller/BookController.java` (SSE 포함 핵심 부분)

```java
package com.example.bookcard.controller;

import com.example.bookcard.dto.BookGenerateRequest;
import com.example.bookcard.entity.Book;
import com.example.bookcard.service.BookService;
import com.example.bookcard.service.NaverSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final NaverSearchService naverSearchService;
    private final ExecutorService sseExecutorService;  // AsyncConfig에서 Bean 주입
    private final ObjectMapper objectMapper;

    // 전체 조회 (페이지네이션 없음, 하위 호환용)
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    // 페이지네이션 조회
    @GetMapping("/paged")
    public ResponseEntity<Page<Book>> getPagedBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(bookService.getPagedBooks(PageRequest.of(page, size)));
    }

    // 네이버 도서 검색
    @GetMapping("/search")
    public ResponseEntity<?> searchBooks(@RequestParam String query) {
        return ResponseEntity.ok(naverSearchService.searchBooks(query));
    }

    // SSE 스트리밍 방식 북카드 생성
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@Valid @RequestBody BookGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);  // 2분 타임아웃

        sseExecutorService.submit(() -> {
            try {
                Book book = bookService.generateBook(request, message -> {
                    try {
                        emitter.send(SseEmitter.event().name("progress").data(message));
                    } catch (IOException e) {
                        log.warn("SSE 전송 실패: {}", e.getMessage());
                    }
                });
                emitter.send(SseEmitter.event().name("complete").data(objectMapper.writeValueAsString(book)));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ex) {
                    log.warn("SSE 에러 전송 실패");
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // 좋아요
    @PostMapping("/{id}/like")
    public ResponseEntity<Book> likeBook(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.likeBook(id));
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }
}
```

**확인:**
- [ ] Postman으로 `POST /api/auth/register` 성공
- [ ] `POST /api/auth/login` → JWT 토큰 수신
- [ ] `Authorization: Bearer {token}` 헤더로 인증 필요 API 접근

---

## Phase 8: 외부 API 연동

### 8.1 NaverSearchService

`service/NaverSearchService.java`

```java
@Service
@Slf4j
public class NaverSearchService {

    @Value("${naver.client.id}")
    private String clientId;

    @Value("${naver.client.secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<Map<String, Object>> searchBooks(String query) {
        String url = "https://openapi.naver.com/v1/search/book.json?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&display=10";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        return (List<Map<String, Object>>) response.getBody().get("items");
    }
}
```

### 8.2 OpenAiService (Prompt Chaining)

4단계 파이프라인:
1. `analyzeBook()` → 책 장르/분위기/테마 분석
2. `generateSummary()` → 3줄 요약 생성
3. `generateImagePrompt()` → DALL-E용 이미지 프롬프트
4. `generateImage()` → DALL-E 이미지 생성

```java
public Book generateWithChaining(BookGenerateRequest request, Consumer<String> progressCallback) {
    progressCallback.accept("책을 분석하고 있습니다...");
    Map<String, Object> analysis = analyzeBook(request);

    progressCallback.accept("요약을 생성하고 있습니다...");
    List<String> summary = generateSummary(request, analysis);

    progressCallback.accept("이미지 프롬프트를 생성하고 있습니다...");
    String imagePrompt = generateImagePrompt(request, analysis);

    progressCallback.accept("AI 이미지를 생성하고 있습니다...");
    String imageUrl = generateImage(imagePrompt);

    return Book.builder()
            .title(request.title())
            .author(request.author())
            .publisher(request.publisher())
            .isbn(request.isbn())
            .originalImage(request.originalImage())
            .description(request.description())
            .summary(summary)
            .generatedImage(imageUrl)
            .build();
}
```

---

## Phase 9: 전역 예외처리

`config/GlobalExceptionHandler.java`

```java
package com.example.bookcard.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // @Valid 검증 실패 → 400 + 필드별 에러 메시지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(errors);
    }

    // 비즈니스 로직 오류 (이메일 중복, ID 없음 등) → 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // 권한 없음 (타인 북카드 삭제 시도 등) → 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    // 로그인 실패 → 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    // 그 외 모든 예외 → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버 오류가 발생했습니다."));
    }
}
```

---

## Phase 10: 테스트 코드

### 10.1 컨텍스트 로드 테스트 (수정 필수)

`test/.../BookcardApplicationTests.java`

```java
@SpringBootTest
@ActiveProfiles("dev")  // MySQL 없이 H2로 테스트 (없으면 prod 프로필로 MySQL 연결 시도)
class BookcardApplicationTests {
    @Test
    void contextLoads() {}
}
```

### 10.2 AuthService 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @InjectMocks AuthService authService;

    @Test
    void register_성공() {
        RegisterRequest request = new RegisterRequest("test@test.com", "password123", "홍길동");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtUtil.generateToken(anyString())).thenReturn("token123");

        AuthResponse response = authService.register(request);

        assertNotNull(response.token());
        assertEquals("test@test.com", response.email());
    }

    @Test
    void register_이메일중복_예외() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
            () -> authService.register(new RegisterRequest("dup@test.com", "pw", "name")));
    }
}
```

### 10.3 BookService 단위 테스트

핵심 케이스:
- `getAllBooks()` → 목록 반환 확인
- `generateBook()` ISBN 중복 → `IllegalArgumentException`
- `generateBook()` ISBN null → 중복 체크 없이 생성 성공
- `deleteBook()` 본인 → 성공
- `deleteBook()` 타인 → `AccessDeniedException`
- `likeBook()` → likeCount + 1 확인

### 10.4 JwtUtil 단위 테스트

```java
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();
        Field secretField = JwtUtil.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(jwtUtil, "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm");

        Field expirationField = JwtUtil.class.getDeclaredField("expirationMs");
        expirationField.setAccessible(true);
        expirationField.set(jwtUtil, 86400000L);
    }

    @Test
    void 토큰_생성_성공() {
        assertNotNull(jwtUtil.generateToken("test@test.com"));
    }

    @Test
    void 이메일_추출_성공() {
        String token = jwtUtil.generateToken("test@test.com");
        assertEquals("test@test.com", jwtUtil.extractEmail(token));
    }
}
```

**확인:**
- [ ] `./gradlew test` 전체 테스트 통과

---

## Phase 11: Frontend 개발

### 11.1 프로젝트 생성

```bash
npm create vite@latest frontend -- --template react
cd frontend
npm install react-router-dom lucide-react
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

### 11.2 구현 순서

```
1. api/authApi.js     ← 인증 API (login, register, logout, getToken, isLoggedIn)
2. api/bookApi.js     ← 북카드 API (getAuthHeader 포함)
3. pages/Login.jsx
4. pages/Register.jsx
5. App.jsx            ← 라우팅 (/login, /register 추가)
6. pages/Library.jsx  ← 북카드 목록
7. pages/Main.jsx     ← 검색 + 생성 (SSE 연결)
8. components/        ← BookCard, SearchBar 등
```

### 11.3 authApi.js

```javascript
const API_BASE = 'http://localhost:8080';

export const authApi = {
    login: async (email, password) => {
        const res = await fetch(`${API_BASE}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        if (!res.ok) throw new Error('로그인 실패');
        return res.json();  // { token, email, nickname }
    },

    register: async (email, password, nickname) => {
        const res = await fetch(`${API_BASE}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, nickname })
        });
        if (!res.ok) throw new Error('회원가입 실패');
        return res.json();
    },

    logout: () => localStorage.removeItem('token'),
    getToken: () => localStorage.getItem('token'),
    isLoggedIn: () => !!localStorage.getItem('token'),
};
```

### 11.4 bookApi.js (인증 헤더 포함)

```javascript
const API_BASE = 'http://localhost:8080';

const getAuthHeader = () => {
    const token = localStorage.getItem('token');
    return token ? { 'Authorization': `Bearer ${token}` } : {};
};

export const bookApi = {
    getAll: () => fetch(`${API_BASE}/api/books`).then(r => r.json()),

    search: (query) => fetch(`${API_BASE}/api/books/search?query=${query}`).then(r => r.json()),

    // SSE 방식 생성 (인증 필요)
    generateStream: (request) => {
        return new EventSource(/* ... */);
        // 실제로는 fetch + ReadableStream 또는 EventSourcePolyfill 사용
    },

    deleteBook: (id) => fetch(`${API_BASE}/api/books/${id}`, {
        method: 'DELETE',
        headers: getAuthHeader()
    }),

    likeBook: (id) => fetch(`${API_BASE}/api/books/${id}/like`, {
        method: 'POST',
        headers: getAuthHeader()
    }).then(r => r.json()),
};
```

### 11.5 (미구현) AuthContext - 향후 추가 권장

현재 localStorage에 직접 접근하는 방식이지만, 전역 상태 관리를 위해 AuthContext 추가 권장:

```javascript
// contexts/AuthContext.jsx
export function AuthProvider({ children }) {
    const [user, setUser] = useState(null);

    const login = async (email, password) => {
        const data = await authApi.login(email, password);
        localStorage.setItem('token', data.token);
        setUser({ email: data.email, nickname: data.nickname });
    };

    const logout = () => {
        authApi.logout();
        setUser(null);
    };

    // 앱 시작 시 토큰 있으면 user 상태 복원
    useEffect(() => {
        const token = authApi.getToken();
        if (token) {
            // /api/auth/me 엔드포인트로 사용자 정보 복원 (현재 미구현)
        }
    }, []);

    return (
        <AuthContext.Provider value={{ user, login, logout, isLoggedIn: !!user }}>
            {children}
        </AuthContext.Provider>
    );
}
```

---

## 최종 디렉토리 구조

```
bookcard/
├── CLAUDE.md
├── DEVELOPMENT_GUIDE.md
├── PROJECT_SPECIFICATION.md
├── build.gradle
├── .env                            ← gitignore 필수
│
├── src/
│   ├── main/
│   │   ├── java/com/example/bookcard/
│   │   │   ├── BookcardApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AsyncConfig.java          ← ExecutorService Bean
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── JwtAuthFilter.java
│   │   │   │   ├── JwtUtil.java
│   │   │   │   ├── PasswordEncoderConfig.java ← 순환참조 해결용 분리
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   └── BookController.java
│   │   │   ├── dto/
│   │   │   │   ├── BookGenerateRequest.java
│   │   │   │   └── auth/
│   │   │   │       ├── AuthResponse.java
│   │   │   │       ├── LoginRequest.java
│   │   │   │       └── RegisterRequest.java
│   │   │   ├── entity/
│   │   │   │   ├── Book.java
│   │   │   │   └── User.java
│   │   │   ├── repository/
│   │   │   │   ├── BookRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   └── service/
│   │   │       ├── AuthService.java          ← implements UserDetailsService
│   │   │       ├── BookService.java
│   │   │       ├── ImageStorageService.java
│   │   │       ├── NaverSearchService.java
│   │   │       └── OpenAiService.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/example/bookcard/
│           ├── BookcardApplicationTests.java  ← @ActiveProfiles("dev") 필수
│           ├── AuthServiceTest.java
│           ├── BookServiceTest.java
│           └── JwtUtilTest.java
│
└── frontend/
    ├── src/
    │   ├── api/
    │   │   ├── authApi.js
    │   │   └── bookApi.js
    │   ├── pages/
    │   │   ├── Login.jsx
    │   │   ├── Register.jsx
    │   │   ├── Library.jsx
    │   │   └── Main.jsx
    │   ├── components/
    │   │   └── ...
    │   └── App.jsx
    └── package.json
```

---

## 트러블슈팅

### 문제 1: 순환참조 (Circular Dependency)

**증상:** 서버 시작 시 `The dependencies of some of the beans in the application context form a cycle` 오류

**원인:** SecurityConfig에서 PasswordEncoder를 Bean으로 정의하면서 동시에 JwtAuthFilter(AuthService를 의존)를 주입받을 때 발생

**해결:**
- `PasswordEncoderConfig.java`를 별도 `@Configuration` 클래스로 분리
- `SecurityConfig`는 PasswordEncoder를 직접 정의하지 않고 주입받기만 함
- `AuthenticationManager`는 `AuthService`가 아닌 `AuthController`에 주입

### 문제 2: 테스트 실패 (MySQL 연결 오류)

**증상:** `BookcardApplicationTests.contextLoads()` 실패, MySQL 연결 불가

**원인:** 기본 프로필이 `prod`로 설정되어 MySQL에 연결 시도

**해결:** 테스트 클래스에 `@ActiveProfiles("dev")` 추가하여 H2 사용

### 문제 3: JWT 의존성 API 변경

**증상:** `io.jsonwebtoken:jjwt-api:0.12.x` 사용 시 기존 예제 코드 동작 안 함

**원인:** 0.11.x → 0.12.x에서 API가 크게 변경됨

**해결:** 0.12.x 방식으로 작성:
```java
// 토큰 생성
Jwts.builder().subject(email).signWith(key).compact()

// 파싱
Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
```

### 문제 4: @Builder.Default 누락

**증상:** Book 생성 후 `likeCount`가 null → NullPointerException

**원인:** `@Builder` 사용 시 필드 초기화 값이 무시됨

**해결:** `@Builder.Default` 어노테이션 추가
```java
@Builder.Default
private Integer likeCount = 0;
```

### 문제 5: H2 콘솔 접근 불가

**증상:** `/h2-console`에 접근하면 403

**원인:** Spring Security가 기본적으로 `/h2-console`을 차단, H2 iframe도 차단

**해결:** SecurityConfig에 추가:
```java
.requestMatchers("/h2-console/**").permitAll()
// 그리고 H2 iframe 허용
.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
```

---

## 빌드/실행 명령어

```bash
# 빌드
./gradlew build

# 실행 (dev 프로필 - H2)
./gradlew bootRun

# 실행 (prod 프로필 - MySQL)
./gradlew bootRun --args='--spring.profiles.active=prod'

# 테스트
./gradlew test

# 프론트엔드
cd frontend
npm run dev
```
