# ADR 001 — 단일 Gradle 모듈

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

`market` / `journal` / `position` / `indicator` / `projection` / `backtest` 여섯 개의 논리 모듈이 있고,
모듈 간 의존 방향에 규칙이 있다 (architecture.md). 이 경계를 Gradle 서브프로젝트로 나눌 수도 있다.

## 결정

**단일 Gradle 모듈로 간다.** 경계는 패키지 구조와 ArchUnit 규칙으로 강제한다.

## 근거

- 사용자가 1명이고 배포 단위가 하나다. 모듈을 쪼개도 따로 배포할 대상이 없다.
- 멀티모듈의 실질 이득은 빌드 병렬화와 의존성 격리인데, 이 규모에서는 둘 다 체감되지 않는다.
  반면 빌드 스크립트 중복, IDE 설정, 버전 정렬 비용은 즉시 발생한다.
- 의존 방향 위반은 컴파일 에러가 아니어도 **빌드 실패**로 잡을 수 있다.
  ArchUnit 규칙 2·4·5 가 그 역할을 한다 (`ArchitectureRules`).

## 결과

- 모듈 경계 위반을 컴파일러가 잡아주지 않는다. 대신 `check` 가 잡는다.
  ArchUnit 규칙이 무력화되면 경계가 즉시 무너지므로, 규칙이 실제로 작동하는지를
  `ArchitectureRulesViolationTest` 로 상시 검증한다.
- 나중에 배포 단위를 분리해야 하면 패키지 구조가 이미 모듈 경계와 일치하므로 추출 비용이 낮다.

## 관련

- [002 — 부분 헥사고날](002-partial-hexagonal.md)
- [003 — 도메인형 패키지 구조](003-domain-package-structure.md)
