# 아키텍처

## 결정: 부분 헥사고날

전면 헥사고날을 채택하지 않는다. **구현체가 둘 이상 존재하거나 존재할 예정인 경우에만** 포트를 정의한다.

| 모듈 | 방식 | 근거 |
|---|---|---|
| `market` | 포트/어댑터 | 캔들 소스가 둘 이상 (실시간 API / 저장된 과거 데이터 / 테스트) |
| `journal` | 포트/어댑터 | DB 없이 도메인 테스트를 돌리기 위한 인메모리 어댑터 필요 |
| `position` | 계층형 | 외부 의존 없음. 순수 계산 |
| `indicator` | 계층형 | 외부 의존 없음. 순수 함수 |
| `projection` | 계층형 | 외부 의존 없음 |
| `backtest` | 계층형 | `market` 포트를 소비하는 쪽. 자체 포트 불필요 |

`backtest`가 백테스트 시에는 과거 캔들 어댑터를, 실사용 시에는 실시간 어댑터를 같은 포트로 소비한다. 이 지점이 없었다면 전부 계층형으로 충분했다.

**구현체가 하나뿐인 인터페이스는 만들지 않는다.**

## 패키지 구조

```
com.coinwin
├── common/
│   ├── domain/                  # Money, Price, Quantity, Percentage
│   └── config/
│
├── market/                      # ◆ 포트/어댑터
│   ├── domain/
│   ├── application/
│   │   ├── port/in/             # LoadMarketDataUseCase
│   │   ├── port/out/            # LoadCandlesPort, SaveCandlesPort,
│   │   │                        #   LoadMarketMetricsPort
│   │   └── service/
│   └── adapter/
│       ├── in/web/
│       └── out/
│           ├── binance/         # BinanceCandleAdapter, BinanceMarketMetricsAdapter
│           ├── persistence/     # JdbcCandleAdapter
│           ├── snapshot/        # ClasspathLeverageBracketAdapter
│           └── memory/          # InMemoryCandleAdapter
│
├── journal/                     # ◆ 포트/어댑터
│   ├── domain/
│   ├── application/
│   │   ├── port/in/             # RecordTradeUseCase, QueryJournalUseCase
│   │   ├── port/out/            # LoadTradesPort, SaveTradePort
│   │   └── service/             # TradeJournalService
│   └── adapter/
│       ├── in/web/              # TradeJournalController
│       └── out/
│           ├── persistence/     # JpaTradeAdapter (+ 엔티티·매퍼·QueryDSL 조건)
│           └── memory/          # InMemoryTradeAdapter
│
├── position/                    # 계층형
│   ├── domain/ application/ api/
├── indicator/
│   ├── domain/ application/
├── backtest/
│   ├── domain/                  # 대·전략·엔진. 포트도 캔들 조회도 모른다
│   ├── application/             # BacktestService — @StoredCandles 포트 소비
│   └── api/
└── projection/
    ├── domain/ api/
```

## 의존 방향

**포트/어댑터 모듈**

```
adapter.in ──→ application.port.in
                      │
                      ▼
              application.service ──→ domain
                      │
                      ▼
              application.port.out  ←── adapter.out
                                        (어댑터가 포트를 구현)
```

**계층형 모듈**

```
api → application → domain
```

`api`와 `adapter`는 같은 바깥 층이다. 둘 다 아무에게도 참조되지 않고, 둘 다 `application`과
`domain`을 참조할 수 있다. ArchUnit 규칙 2 가 이 형태로 정의돼 있다 —
`adapter.in`만 `api` 층에 얹어 두면 `adapter.out`이 어느 층에도 속하지 않게 되고,
아웃바운드 어댑터가 포트를 구현하는 것(헥사고날의 정의 그 자체)이 전부 위반으로 잡힌다.

**모듈 간**

```
common              ← 모든 모듈 (역방향 금지)
backtest            → indicator, position,
                      market.application.port.out, market.domain,
                      journal.domain, projection.domain
indicator           → market.domain
journal             → position, indicator.domain
position.application → market.application.port.in, market.domain
그 외 모듈 간 직접 참조 금지
```

`indicator`와 `position`은 서로를 모른다. 조합은 `backtest`와 각 모듈 `application`에서만.

**`market.domain` 이 세 모듈에 열려 있는 이유**는 `Candle` 때문이다. `market`이 생산하고
`indicator`·`backtest`가 소비하는 공통 어휘이며, 포트의 반환 타입이므로 포트를 쓰는 쪽은
어차피 이 타입을 본다. `common`으로 올리는 선택지도 있었으나 그러면 `common`이 반올림 정책을
가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. 근거는 `docs/adr/013`.

**`position.application → market.application.port.in`** 은 구간별 유지증거금 때문이다.
청산가는 거래소 레버리지 구간표에 의존하는데, `position/domain`이 그 구현을 들고 있으면
도메인에 `position → market` 의존이 생긴다. 인터페이스는 `position/domain`에 두고 구현만
`position/application`에서 `market`의 인바운드 포트를 소비한다. 근거는 `docs/adr/008`.
아웃바운드가 아니라 인바운드를 쓰는 이유는, "구간표를 어디서 얻는가"가 `market`의 정책이기
때문이다.

**`journal → indicator.domain`** 은 `BandPosition` 하나 때문이다. 진입 시점에 가격이 구름과
밴드의 어느 쪽에 있었는지를 기록하는데, 그 세 값은 Phase 4 에서 이미 확정돼 있다. `journal`에
같은 뜻의 enum 을 또 두면 "경계는 구간에 포함된다"는 규칙이 한쪽만 바뀌는 순간 두 모듈이
다른 답을 낸다. `common`으로 올리지 않은 이유는 `Candle`과 같다 — `common`이 반올림 정책을
가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. 근거는 `docs/adr/017`.
**`journal`은 지표를 계산하지 않는다.** 계산기도 `IchimokuValue`도 참조하지 않고 판정 결과만
적는다. 그 이상을 끌어오게 되면 이 의존을 다시 봐야 한다.

**`backtest → journal.domain, projection.domain`** 은 어휘를 나누기 위해서다. 백테스트가 낸
거래와 실제로 한 매매가 둘 다 `ClosedTrade` 이므로 `JournalSummary` 를 양쪽에 그대로 씌울 수
있다 — **검증한 전략과 실제 기록을 같은 기준으로 비교할 수 있다는 뜻이다.** 따로 정의하면
"0 원은 승리가 아니다"(`TradeTally`), "반사실에서 펀딩비를 빼지 않는다"(`ClosedTrade`),
"최대낙폭은 직전 고점 대비"(`EquityCurve`) 같은 규칙이 두 곳에서 갈라지고, 그러면 두 수치를
나란히 놓는 것 자체가 무의미해진다. `journal.domain` 은 프레임워크 의존이 없으므로 이 의존이
백테스트를 DB 나 Spring 에 묶지 않는다. 근거는 `docs/adr/018`.
**방향은 한쪽뿐이다** — `journal` 은 `backtest` 를 모른다. `MarketContext` 의 지지·저항을
`PriceZone` 으로 구조화하는 것은 새 방향을 만드는 별개의 결정이므로 하지 않았다.

**모듈 간 의존은 ArchUnit이 강제하지 않는다.** 아래 6개 규칙 중 어느 것도 모듈 경계를 보지
않는다 — 규칙 3(순환 참조)이 최악의 경우만 막는다. 이 표는 문서와 리뷰가 지킨다.
Phase 6 에서 `backtest`가 다섯 모듈을 조합하게 됐고, 그럼에도 규칙으로 세우지 않았다 —
규칙 5 가 `backtest → market.adapter` 라는 **가장 위험한 한 방향**을 이미 막고 있고, 나머지는
전부 도메인 → 도메인이라 잘못 걸어도 프레임워크 오염이 아니라 응집도 문제에 그친다.

## ArchUnit 강제 규칙

아래 6개는 테스트로 강제된다. 위반 시 빌드 실패.

1. `domain` 패키지의 Spring / JPA / Jackson import 금지
2. 계층 의존 방향 (`(api|adapter) → application → domain`)
3. 패키지 순환 참조 0건
4. `market.application` / `journal.application` → `adapter` 참조 금지
5. `backtest` → `market.adapter` 참조 금지 (포트만 허용)
6. `adapter.out` 구현체는 반드시 `application.port.out` 인터페이스를 구현

4번과 5번이 없으면 헥사고날이 이름만 남고 계층형으로 무너진다.

**`allowEmptyShould` 는 이제 하나도 없다.** 대상 패키지가 아직 없는 규칙을 통과시키던 임시
플래그였고, 마지막 하나(규칙 5)가 Phase 6 에서 빠졌다. 여섯 규칙이 전부 실제 클래스를 센다.
플래그가 되돌아오는 것은 사람의 기억이 아니라 테스트가 막는다 —
`ArchitectureRulesTest.어떤_규칙도_빈_매칭을_허용하지_않는다` 가 `ArchitectureRules` 소스에
`allowEmptyShould(` 가 없는지 매 빌드 검사한다.

## 포트 명명 규약

- 인바운드: `~UseCase` (예: `LoadMarketDataUseCase`)
- 아웃바운드: `~Port` (예: `LoadCandlesPort`)
- 어댑터: `{기술}{대상}Adapter` (예: `BinanceCandleAdapter`)

**포트는 좁게 정의한다.** `MarketPort` 하나에 메서드 10개를 몰지 않고 역할별로 분리한다. 인터페이스 분리 원칙을 지켜야 테스트 더블이 단순해진다.
