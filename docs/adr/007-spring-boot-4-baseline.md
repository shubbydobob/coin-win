# ADR 007 — Spring Boot 4.1 을 기준선으로 채택

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

`CLAUDE.md` 초안은 `Java 21 / Spring Boot 3.x` 로 적혀 있었다.
Phase 0 착수 시점에 실제 버전을 확인한 결과:

- **Spring Boot 3.5 는 2026-06-30 에 OSS EOL** 에 도달했다. 마지막 무료 릴리스는 3.5.16 (2026-06-25).
  이후 보안 패치는 상용 연장 지원에서만 나온다.
- Spring Boot 4.1.1 이 현재 지원 라인이고, Java 17~26 을 지원한다.

## 결정

**Spring Boot 4.1.1 / Java 21 (toolchain 고정) / Gradle 9.7.1** 을 기준선으로 한다.
`CLAUDE.md` 의 `Spring Boot 3.x` 표기도 함께 갱신한다.

## 근거

- **지금이 전환 비용이 0 인 유일한 시점이다.** Phase 0 착수 시점에 프로덕션 코드가 0줄이었다.
  Phase 3(시장 데이터) 이나 Phase 5(매매 기록) 에 가서 올리면 어댑터와 영속성 코드를 들고
  마이그레이션해야 한다.
- 보안 패치가 끊긴 프레임워크로 새 프로젝트를 시작할 이유가 없다.
- Java 는 21 로 고정한다. Boot 4.1 이 26 까지 지원하지만, LTS 를 벗어날 이유가 없고
  toolchain 을 고정하면 CI 와 로컬이 갈라지지 않는다.

## 결과 — 이 결정이 실제로 바꾼 것들

Boot 4 는 메이저 업그레이드라서 아래가 따라왔다. 전부 Phase 0 에 반영돼 있다.

### JUnit Jupiter 6.0.3

Boot 4 의 `spring-boot-starter-test` 가 JUnit 6 를 가져온다.

- ArchUnit 은 `archunit-junit5` 가 아니라 **`archunit-junit6`** 를 써야 한다 (1.5.0 에서 추가된 모듈).
- Gradle 9 + JUnit 6 조합에서 `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` 를
  명시하지 않으면 *"Failed to load JUnit Platform"* 으로 깨진다.

### Jackson 3 (`tools.jackson`)

Boot 4 는 Jackson 3 를 기본으로 쓰고 Jackson 2(`com.fasterxml.jackson`)를 병행 제공한다.

**도메인 순수성 규칙에서 `com.fasterxml.jackson..` 하나만 막으면 구멍이 뚫린다.**
`tools.jackson..` 도 같이 막아야 한다. 두 곳에 반영했다.

- `ArchitectureRules#domainIsFrameworkFree` (ArchUnit 규칙 1)
- `.claude/scripts/guard-domain-purity.sh` (파일이 써지기 전에 차단하는 훅)

### 그 외 관리 버전

Spring Framework 7.0.9 / Hibernate 7.4.5 / Jakarta Persistence 3.2.0 / Testcontainers 2.0.5.
Phase 3 (Testcontainers 통합 테스트) 과 Phase 5 (JPA 어댑터) 에서 영향을 받는다.

### QueryDSL

Phase 5 에서 쓸 QueryDSL 은 원본 `com.querydsl` 이 2024-11 이후 미유지 상태다.
유지되는 포크인 **`io.github.openfeign.querydsl:7.6`** 을 쓴다.
Phase 0 에서는 의존성을 추가하지 않고 좌표만 기록해 둔다 (YAGNI).

## 관련

- [004 — BigDecimal 대신 값 객체](004-value-objects-over-bigdecimal.md)
- `gradle/libs.versions.toml` — 모든 버전의 단일 출처
