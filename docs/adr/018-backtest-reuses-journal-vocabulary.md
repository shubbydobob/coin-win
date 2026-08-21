# ADR 018 — 백테스트는 매매 기록의 어휘를 그대로 쓴다

- 날짜: 2026-08-22
- 상태: 채택
- 관련: `002` (부분 헥사고날), `013` (Candle 은 market.domain 에), `016` (journal 의 JPA),
  `017` (journal → indicator.domain)

## 맥락

Phase 6 의 `SimulatedTrade` 가 필요로 하는 것들이 `journal/domain` 에 **이미 정확히 같은
의미로** 존재했다.

| 필요한 것 | 이미 있는 곳 |
|---|---|
| 진입 체결 내역 | `journal.domain.ExecutedEntries` · `Fill` |
| 청산 | `journal.domain.Exit` · `TradeClosure` · `ExitReason` |
| 수수료·펀딩비 | `journal.domain.TradeCosts` |
| 진입 시점 지표 판정 | `journal.domain.MarketContext` |
| 손익·반사실·보유기간 | `journal.domain.ClosedTrade` |
| 승률 집계 | `journal.domain.TradeTally` |
| 자산 곡선·최대낙폭 | `projection.domain.EquityCurve` |

그런데 architecture.md 의 모듈 간 의존 표에 `backtest → journal` 도 `backtest → projection`
도 없었다. 표는 `backtest → indicator, position, market.application.port.out, market.domain`
까지였다.

## 선택지

**1. 의존 표에 두 방향을 추가한다.**
가장 적은 코드. 다만 백테스트가 매매 기록 모듈에 의존하는 그림이 되어 "기록" 과 "시뮬레이션"
의 경계가 흐려진다.

**2. 일곱 타입을 `common/domain` 으로 올린다.**
`Candle` 을 올리지 않은 이유(`docs/adr/013`)와 정확히 같은 문제가 생긴다 — `common` 이
반올림 정책을 가진 값 객체 모음에서 공용 모델 전반으로 넓어진다. 그리고 `ClosedTrade` 는
값 객체가 아니라 도메인 개념이다.

**3. `backtest` 에 따로 정의한다.**
의존은 깨끗하지만 규칙이 두 곳에 생긴다. 구체적으로:

- `ExitReason.honorsPlan()` — 무엇이 계획 준수인가
- `TradeTally` 의 승수 판정 — **0 원으로 끝난 거래는 승리가 아니다**
- `ClosedTrade.pnlAt()` — 방향에 따른 손익 부호
- `lossIfStopHonored()` — 반사실에서 펀딩비를 빼지 않는 이유(`docs/adr/016`)

이 계산 도메인에서 가장 경계해 온 것이 **같은 규칙이 두 곳에서 갈라지는 것**이다.

## 결정

**1 을 택한다.** 의존 표에 다음 둘을 추가한다.

```
backtest → journal.domain, projection.domain
```

## 근거

**`journal.domain` 은 프레임워크 의존이 없는 순수 도메인이다.** JPA 는 `journal.adapter.out`
에 있고(`docs/adr/016`), 규칙 1 이 그것을 강제한다. 그래서 이 의존은 백테스트를 DB 나 Spring
에 묶지 않는다 — `BacktestEngineTest` 는 컨텍스트 없이 순수 JUnit 으로 돈다.

**같은 어휘로 표현되는 것 자체가 값이다.** 백테스트가 낸 거래와 실제로 한 매매가 둘 다
`ClosedTrade` 이므로 `JournalSummary.of` 를 양쪽에 그대로 씌울 수 있다. 검증한 전략과 실제
기록을 나란히 놓고 **같은 기준으로** 볼 수 있다는 뜻이다. 승률이 백테스트에서 62% 였는데
실제로 41% 라면, 그 둘이 다른 코드로 계산된 수치라면 비교 자체가 무의미하다.

**의존 방향이 옳다.** `backtest → journal` 이지 그 반대가 아니다. 매매 기록은 백테스트를
모른다. 시뮬레이션이 기록의 어휘를 빌리는 것이고, 기록이 시뮬레이션에 매이는 것이 아니다.
`journal` 을 Phase 5 에서 먼저 만든 순서와도 맞는다.

**`EquityCurve` 도 같은 논리다.** `maxDrawdown` 은 "직전 고점 대비 가장 깊었던 하락" 이라는
한 문장이고, 그 문장이 두 곳에 구현되면 몬테카를로가 낸 낙폭과 백테스트가 낸 낙폭을 비교할 수
없다. Phase 2 가 "거래 내부 왕복이 모델에 없어 낙폭이 실제보다 얕다" 는 한계를 명시해 두었는데
(`docs/adr/010`), **그 한계를 수치로 확인하려면 두 낙폭이 같은 정의여야 한다.**

## 대가

**`TradeTally.over` 를 공개로 바꿨다.** 원래 `journal.domain` 안에서만 쓰이던 것이다. 그
record 의 존재 이유가 "두 무리의 승률을 각각 다른 코드로 내면 무엇을 승리로 세는가가 갈린다"
였으므로, 세 번째 사용처가 생긴 지금 공개하는 것이 그 이유에 부합한다.

**모듈 경계가 하나 느슨해졌다.** `journal.domain` 이 이제 두 모듈에서 소비되므로 여기에
`journal` 고유의 관심사(예: 사용자가 손으로 적는 메모 필드)를 더할 때 `backtest` 를 함께
봐야 한다. 그 부담이 커지면 공통 부분을 `trade` 같은 모듈로 뽑는 것이 다음 수순이다.

**`MarketContext.rationale` 을 백테스트가 지어낸다.** 이 필드는 비면 거부되고(`docs/adr/017`),
사람이 쓰는 자유 텍스트를 전제한 것이었다. 백테스트에는 쓸 사람이 없으므로 `PriceZone` 이
자기를 문장으로 설명한다 — `"지지대 59000.00~59200.00 (터치 3회) 근단 반전 진입"`. 구간과
터치 횟수가 대의 정체 전부이므로 사후에 "왜 여기서 들어갔는가" 에 답하는 데 부족하지 않다.

## 이 결정이 강제하지 않는 것

**`journal` 이 `backtest` 를 참조하는 것은 여전히 금지다.** `MarketContext` 의 지지·저항을
`PriceZone` 으로 구조화하고 싶어지겠지만, 그것은 `journal → backtest` 라는 새 방향을 만드는
별개의 결정이다. Phase 6 범위 밖으로 둔다.

**ArchUnit 은 이 표를 강제하지 않는다.** 여섯 규칙 중 어느 것도 모듈 경계를 보지 않고,
규칙 3(순환 참조)이 최악의 경우만 막는다. 이 문서와 리뷰가 지킨다 — architecture.md 가
Phase 5 에서 이미 그렇게 적어 두었다.
