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
│   │   ├── port/out/            # TradeRepositoryPort
│   │   └── service/
│   └── adapter/
│       ├── in/web/
│       └── out/{persistence,memory}/
│
├── position/                    # 계층형
│   ├── domain/ application/ api/
├── indicator/
│   ├── domain/ application/
├── backtest/
│   ├── domain/ application/ api/
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
                      market.application.port.out, market.domain
indicator           → market.domain
journal             → position
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

## ArchUnit 강제 규칙

아래 6개는 테스트로 강제된다. 위반 시 빌드 실패.

1. `domain` 패키지의 Spring / JPA / Jackson import 금지
2. 계층 의존 방향 (`(api|adapter) → application → domain`)
3. 패키지 순환 참조 0건
4. `market.application` / `journal.application` → `adapter` 참조 금지
5. `backtest` → `market.adapter` 참조 금지 (포트만 허용)
6. `adapter.out` 구현체는 반드시 `application.port.out` 인터페이스를 구현

4번과 5번이 없으면 헥사고날이 이름만 남고 계층형으로 무너진다.

## 포트 명명 규약

- 인바운드: `~UseCase` (예: `LoadMarketDataUseCase`)
- 아웃바운드: `~Port` (예: `LoadCandlesPort`)
- 어댑터: `{기술}{대상}Adapter` (예: `BinanceCandleAdapter`)

**포트는 좁게 정의한다.** `MarketPort` 하나에 메서드 10개를 몰지 않고 역할별로 분리한다. 인터페이스 분리 원칙을 지켜야 테스트 더블이 단순해진다.
