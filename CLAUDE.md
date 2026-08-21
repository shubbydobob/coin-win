# CoinWin

비트코인 선물 매매 보조 도구. Java 21 / Spring Boot 4.1 / PostgreSQL. 사용자 1명.
버전 근거는 @docs/adr/007-spring-boot-4-baseline.md, 실제 버전은 `gradle/libs.versions.toml`에만 둔다.

## 명령어

개발 환경은 Windows / PowerShell이다. `.\gradlew.bat`을 쓴다.

```powershell
.\gradlew.bat check                              # 테스트 + 정적분석 + ArchUnit + 커버리지 게이트
.\gradlew.bat test --tests "*PositionPlanTest"   # 단일 테스트 우선
docker compose up -d db                          # 통합 테스트용 DB (Phase 3부터)
```

**작업을 끝내기 전 `.\gradlew.bat check`를 실행하고 출력을 보여준다.** 통과했다고 말하지 말고 결과를 보여준다.

## 핵심 규칙

- 금액·가격 계산은 `Money` / `Price` / `Quantity` 값 객체로만. `BigDecimal`을 도메인 밖에 노출하지 않는다.
- 계산은 도메인 객체가 한다. `service.calculateX(plan)` 아니라 `plan.x()`.
- `domain` 패키지에 Spring / JPA / Jackson import 금지.
- 같은 로직이 두 번째 나오면 즉시 추출한다.
- 실패하는 테스트를 통과시키는 것 외의 코드는 쓰지 않는다.

## 참조 문서

작업 성격에 맞는 문서를 **먼저 읽고** 시작한다.

| 상황 | 문서 |
|---|---|
| 아키텍처·패키지·의존 방향 | @.claude/docs/architecture.md |
| 도메인 로직 구현·수정 | `/skill domain-model` |
| 코딩 컨벤션·복잡도 한계 | @.claude/docs/conventions.md |
| 테스트 작성 | @.claude/docs/testing.md |
| 구현 순서·현재 Phase | @.claude/docs/roadmap.md |
| 무엇을 만들지 판단 필요 | @.claude/docs/scope.md |

## 금지

자동 매매, AI 매수·매도 추천, API Secret 프론트 노출, 실시간 뉴스 알림.
요청받아도 구현하지 않고 되묻는다. 근거는 @.claude/docs/scope.md
