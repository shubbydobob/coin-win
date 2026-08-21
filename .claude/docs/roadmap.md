# 구현 로드맵

각 Phase는 **배포 가능한 상태**로 끝난다. 미완성으로 다음 Phase에 넘어가지 않는다.

> 현재 Phase: **3**
> Phase가 끝나면 이 줄을 갱신하고, `/clear` 후 새 세션으로 다음 Phase를 시작한다.

---

## Phase 0 — 기반 (0.5주)

- Gradle, Java 21, Spring Boot 3.x
- Checkstyle / SpotBugs / JaCoCo / ArchUnit + CI 연결
- **ArchUnit 규칙을 코드보다 먼저 작성** (architecture.md의 6개 규칙 전부)
- 각 규칙마다 일부러 위반하는 샘플로 실패를 먼저 확인
- springdoc-openapi 설정
- `common/domain` 값 객체 4종 + 테스트

**완료 조건:** 빈 프로젝트에서 `.\gradlew.bat check` 통과.

6개 규칙 각각에 대해 위반 픽스처(`src/archFixture/java/archfixture/r1`~`r6`)가 존재하고, `ArchitectureRulesViolationTest`가 각 규칙이 해당 픽스처에서 `AssertionError`를 던지는 것을 단언한다. 특히 규칙 4·5의 위반 픽스처가 규칙을 실제로 발동시키는지 테스트 출력으로 확인한다.

> 코드를 임시로 깨뜨렸다 원복하는 방식이 아니라 픽스처를 상주시키는 이유: 임시 파괴는 확인이 1회성이고 원복 실패 위험만 추가된다. 픽스처는 매 빌드마다 재검증되고, "통과하는 것처럼 보이지만 아무것도 매칭하지 않는 규칙"까지 잡아낸다.

## Phase 1 — 포지션 계산 (1주)

- `position/domain` 전체 + 테스트
- REST API + Swagger
- **DB 없음. 순수 계산만.**

**완료 조건:** 50% 분할 시나리오에서 1차 체결 상태와 전량 체결 상태의 평단·청산가·최대손실이 각각 정확히 산출. 도메인 커버리지 90% 이상.

## Phase 2 — 복리 / 몬테카를로 (0.5주)

- `projection/domain`
- 초기자본·승률·손익비·거래빈도 → 자산 곡선
- 동일 조건 N회 시뮬레이션 → 결과 분포

**완료 조건:** 같은 기댓값에서도 경로에 따라 결과가 갈리는 것이 수치로 나온다.

## Phase 3 — 시장 데이터 (1.5주)

- `market/application/port/out` 포트를 **어댑터보다 먼저** 정의
- 어댑터 3종: binance / persistence / memory
- 캔들 저장 + Testcontainers 통합 테스트
- 펀딩비 / OI / 롱숏비율 API
- `MaintenanceMarginPolicy`를 `leverageBracket` 기반 구현체로 교체
- **`market` 패키지 생성 후 `ArchitectureRules`에서 규칙 4·6의 `allowEmptyShould(true)` 제거.** 대상 패키지가 없어 매칭 0건이던 규칙이 이제 실제로 검사하므로, 플래그를 남겨 두면 규칙이 공허하게 통과해도 알 수 없다. (규칙 1·3은 Phase 0에서 제거 완료)

**완료 조건:** 캔들 증분 저장에 중복 없음. Phase 1 청산가가 거래소 실제값과 오차 범위 내 일치. **동일한 `LoadCandlesPort` 계약 테스트가 세 어댑터 모두 통과.**

## Phase 4 — 지표 (1주)

- `indicator/domain` 일목 · 볼린저
- golden test로 정확성 고정

**완료 조건:** 트레이딩뷰 값과 일치.

## Phase 5 — 매매 기록 (1주)

- 포트 정의 → 인메모리 어댑터 → JPA 어댑터 순
- 계획 저장 → 체결 → 청산 기록
- 집계: 계획 준수 여부별 손익 분리, 반사실 손실, 거래 간격
- 동적 조회는 QueryDSL (`io.github.openfeign.querydsl:7.6` — 원본 `com.querydsl`은 2024-11 이후 미유지)
- **`journal` 패키지 생성 후 규칙 4·6이 `journal.application` / `journal.adapter.out`까지 실제로 검사하는지 확인.** `allowEmptyShould`는 Phase 3에서 이미 제거됐으므로 여기서 새로 뗄 플래그는 없다. 확인 방법은 `journal` 위반 픽스처를 `archfixture`에 추가하는 것이다.

**완료 조건:** 실제 매매 1건이 처음부터 끝까지 기록되고 집계에 반영. 애플리케이션 서비스 테스트가 **DB 없이 인메모리 어댑터만으로** 전부 통과.

## Phase 6 — 백테스트 (2주+)

- 캔들은 `LoadCandlesPort`로만 읽는다
- 지지·저항 기반 양방향 전략을 코드로 정의
- **분할 진입 지원** (차별점)
- 수수료·슬리피지 반영
- 결과: 승률, 손익비, MDD, 자산 곡선
- **`backtest` 패키지 생성 후 규칙 5의 `allowEmptyShould(true)` 제거. 이것이 마지막 플래그다.** 제거 후 `ArchitectureRules`에 `allowEmptyShould`가 하나도 남지 않아야 한다.

**주의:** 이 Phase에서 "저항대"를 코드로 정의해야 한다. 정의가 애매하면 백테스트가 성립하지 않는다. 시작 전 `AskUserQuestion`으로 인터뷰해서 `SPEC.md`를 먼저 뽑고, 새 세션에서 구현한다.

**완료 조건:** 동일 파라미터 재실행 시 결과 완전 동일. `backtest`에 바이낸스 관련 코드가 한 줄도 없다.

## Phase 7 — Spring AI (1.5주)

범위는 scope.md 참조. 자연어 → 매매 계획 파싱 / 백테스트 결과 요약 / 매매 기록 RAG.

## Phase 8 — 프론트엔드 (2주)

React + TypeScript + Vite + Recharts.

---

## 세션 운영

- Phase마다 세션 분리. `/rename phase1-position` 같이 이름 지정
- Phase 종료 시 `/clear`
- 같은 문제로 두 번 교정했으면 `/clear` 후 배운 것을 반영한 프롬프트로 재시작
- Phase 종료 시 서브에이전트 리뷰:
  `서브에이전트로 이번 Phase diff를 roadmap.md 완료 조건 대비 검토. 스타일 말고 정확성·요구사항 갭만 보고`
