# archfixture — 의도적으로 아키텍처 규칙을 위반하는 코드

**이 디렉터리의 코드는 고치면 안 된다. 잘못 짠 것이 아니라 잘못 짜도록 설계된 것이다.**

## 왜 있는가

ArchUnit 규칙이 "통과한다"는 사실만으로는 규칙이 실제로 작동하는지 알 수 없다.
패키지 이름에 오타가 나서 아무 클래스도 매칭하지 않는 규칙도 똑같이 통과하기 때문이다.

여기 있는 6개 클래스는 `ArchitectureRules` 의 6개 규칙을 하나씩 위반한다.
`ArchitectureRulesViolationTest` 가 각 규칙이 이 클래스들에서 **실패하는 것을 단언**한다.
규칙이 무력화되면 위반 테스트가 먼저 깨진다.

## 왜 루트 패키지가 `com.coinwin` 이 아닌가

`ArchitectureRulesTest` 는 `importPackages("com.coinwin")` 으로 정상 코드만 읽는다.
픽스처가 `com.coinwin` 아래 있으면 정상 검사에 섞여 들어가 빌드가 영구히 깨진다.
루트를 분리했기 때문에 두 검사가 서로를 오염시키지 않는다.

## 빌드 설정

`build.gradle.kts` 에서 이 소스셋은 Checkstyle / SpotBugs / JaCoCo 대상에서 제외된다.
의도적으로 나쁜 코드에 스타일 검사를 돌릴 이유가 없다.

| 파일 | 위반하는 규칙 |
|---|---|
| `r1/domain/DirtyDomain.java` | 1 — domain 이 Spring 에 의존 |
| `r2/api/ApiEndpoint.java`, `r2/domain/BackwardDomain.java` | 2 — domain 이 api 를 참조 (역방향) |
| `r3/a/ServiceA.java`, `r3/b/ServiceB.java` | 3 — 패키지 순환 참조 |
| `r4/market/application/LeakyService.java` | 4 — market.application 이 market.adapter 참조 |
| `r5/backtest/LeakyEngine.java` | 5 — backtest 가 market.adapter 참조 |
| `r6/market/adapter/out/OrphanAdapter.java` | 6 — 포트를 구현하지 않는 *Adapter |
