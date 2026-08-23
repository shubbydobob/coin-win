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

## 어디서 작업하는가

**`main` 에 직접 커밋하지 않는다.** 작업은 `dev` 에서 하고, 확인이 끝난 것만 `main` 으로
병합한다.

```
dev ──(작업·커밋)──▶ 확인 ──▶ main   # 병합은 --no-ff, 브랜치는 지우지 않는다
```

커밋이 여럿인 큰 작업은 `dev` 에서 `feat/{요약}` 를 따고 끝나면 `dev` 로 병합한다. Phase 가
남아 있던 동안의 `feat/{phase}-{요약}` 와 같은 규칙이고, 갈라지는 지점만 `main` 에서 `dev` 로
바뀌었다.

**`main` 은 "확인된 것" 이라는 뜻을 갖는다.** 그 뜻은 거기에 확인되지 않은 것이 한 번도 들어가지
않을 때만 성립한다 — `main` 에서 바로 고치기 시작하면 그 순간 `dev` 는 아무 일도 하지 않는
브랜치가 된다.

**여기서 "확인" 은 게이트 통과가 아니다.** `.\gradlew.bat check` 는 커밋 전에 이미 도는 것이고,
병합 조건은 그 위에 **사람이 실제로 확인한 것**이 하나 더 있다는 뜻이다. 이 저장소는 그
차이로 두 번 데였다(Phase 8 의 `findByText` 거짓 초록, 브라우저를 처음 띄웠을 때 드러난 스키마
덮어쓰기).

## 작업을 끝낼 때

순서는 **검사 → 요약 → 승인 → 커밋**이다. 승인 없이 커밋하지 않는다.

### 1. 비개발자가 읽을 수 있는 요약을 먼저 낸다

**무엇을 고쳤는가가 아니라 무엇이 달라지는가를 쓴다.** 이 프로젝트의 사용자는 코드를 읽으려고
이 도구를 쓰는 것이 아니다.

적을 것 — 화면에서 무엇이 달라지는가, 어떤 판단이 가능해졌는가, 무엇을 못 하게 됐는가,
그래서 매매에 무슨 도움이 되는가.

주어로 쓰지 않을 것 — 파일명, 클래스·함수 이름, 프레임워크 이름, 라이브러리 버전. 필요하면
설명 뒤에 괄호로 붙인다. **"`PositionReconciliationPanel` 에 `refetchInterval` 을 걸었다" 가
아니라 "계좌 화면이 15초마다 저절로 갱신된다" 다.**

숫자와 증거는 그대로 둔다. 쉽게 쓰라는 것이지 뭉개라는 것이 아니다 — "성능이 좋아졌다" 는
요약이 아니라 아무 말도 하지 않은 것이다.

**모르는 것은 모른다고 적는다.** 검증하지 못한 자리, 확인이 남은 자리를 요약에서 빼면
승인하는 사람이 다 끝난 것으로 읽는다.

### 2. 승인을 받고 커밋한다

요약을 낸 다음 커밋 여부를 묻는다. **"작업해 줘" 는 커밋해 달라는 뜻이 아니다.**

되돌리기 쉬운 일은 단계마다 되묻지 않지만 커밋은 기록에 남는다. 되돌릴 수 있음과 되돌릴
흔적이 남지 않음은 다르다.

커밋 형식은 @.claude/docs/conventions.md 의 커밋 절을 따른다.

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
