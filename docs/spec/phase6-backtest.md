# Phase 6 — 지지·저항 반전 백테스트 명세

> 이 문서는 구현 전에 확정한 **전략의 코드 정의**다. roadmap.md 가 "정의가 애매하면 백테스트가
> 성립하지 않는다" 며 인터뷰를 먼저 요구한 그 산출물이다. 구현은 이 문서를 근거로 한다.
>
> 확정 경로: 인터뷰 3라운드 (2026-08-22). 미확정 항목은 § 12 에 남겨 두었다.

---

## 1. 전략 한 문장

**피벗 군집으로 만든 지지·저항 대(帶)의 근단에서 반전 방향으로 50% 분할 진입하고, 대 원단
너머에서 손절하며, 반대편 최근접 대에서 익절한다.**

돌파 매매는 하지 않는다. 대에 닿으면 되돌아온다는 쪽에만 건다.

---

## 2. 대상과 파라미터

| 항목 | 값 |
|---|---|
| 종목 | BTCUSDT 무기한 (`Symbol`) |
| 주기 | **15분 · 1시간 · 4시간 · 1일 전부 지원** (`CandleInterval` 파라미터) |
| 캔들 출처 | `LoadCandlesPort` 만. 거래소를 직접 때리지 않는다 |
| 동시 포지션 | **1개.** 열려 있는 동안 새 신호는 버린다 |

주기를 하나로 고정하지 않기로 했으므로 **모든 임계값은 절대 가격이나 고정 백분율이 아니라
ATR 배수로 표현한다.** 15분봉과 일봉에 같은 `0.2%` 를 쓰면 한쪽은 대가 터무니없이 넓거나
좁아지고, 그러면 주기마다 파라미터를 다시 튜닝해야 한다. ATR 배수 하나면 네 주기가 같은
설정으로 돈다.

---

## 3. ATR — 새로 만들어야 하는 지표

`indicator/domain` 에 없다. 대 폭·손절 버퍼·군집 허용치가 전부 ATR 에 매달리므로 Phase 6 의
첫 구현 대상이다.

```java
// indicator/domain
public record AverageTrueRange(int period) {
    static AverageTrueRange standard();                        // 14
    List<IndicatorPoint<Money>> over(CandleSeries series);
}
```

- 반환이 `Money` 인 이유: ATR 은 가격이 아니라 **가격 거리**다. `Price.absoluteDifference` 도
  `PriceBand.width()` 도 이미 `Money` 를 돌려준다. 같은 뜻에 같은 타입을 쓴다.
- **평활은 Wilder RMA 다.** 단순이동평균이 아니다. 트레이딩뷰 `ta.atr` 이 `ta.rma` 를 쓴다.
- **Phase 4 와 같은 방식으로 확정한다** — 트레이딩뷰가 배포하는 Pine 소스 원문과 대조한다.
  값 몇 개를 눈으로 맞대는 것보다 강하다. 근거는 `docs/adr/015` 의 판단을 그대로 따른다.
- golden test 기준값은 **닫힌 식이 나오는 합성 캔들**에서 얻는다. 진폭이 일정한 캔들열의 TR 은
  상수이고, 상수열의 RMA 는 초깃값과 같다 — 손으로 검산되는 값이 나온다.
- 워밍업: 첫 TR 이 index 1(직전 종가 필요), 첫 ATR 이 index `period`. 모자라면
  `InsufficientCandlesException`.

---

## 4. 대(zone) 의 정의

### 4.1 피벗

```java
// backtest/domain
public enum PivotKind { SWING_HIGH, SWING_LOW }
public record Pivot(Instant at, Price price, PivotKind kind) {}
public record PivotDetector(int lookback) {
    List<Pivot> over(CandleSeries series);
}
```

index `i` 가 스윙 고점인 조건은 `high[i]` 가 `high[i-N .. i+N]` 중 **유일한 최대**다.
저점은 `low` 로 대칭. 동률은 피벗이 아니다 — 두 봉이 같은 극값이면 어느 쪽이 그 자리인지
정해지지 않고, 그 모호함이 대의 개수를 데이터에 따라 흔든다.

**`i+N` 이 필요하다는 것이 이 전략의 가장 중요한 제약이다.** 시점 `t` 에서 알 수 있는 피벗은
`t-N` 이하뿐이다. § 8 이 이것을 강제한다.

### 4.2 군집

피벗들을 가격 근접도로 묶는다. 두 피벗이 같은 대에 속하는 조건:

```
|price_a − price_b| ≤ ATR(t) × clusterMultiple
```

- ATR 은 **평가 시점 `t` 의 값**이다. 피벗 발생 시점의 ATR 이 아니다. 대는 "지금 이 변동성에서
  같은 자리로 보이는가" 이고, 그 판정은 지금 한다.
- 군집은 **가격순 단일 패스**로 만든다. 정렬한 피벗을 훑으며 직전 피벗과의 간격이 허용치
  이내면 같은 군집에 넣고, 아니면 새 군집을 연다. 결과가 입력 순서에 의존하지 않아
  결정론이 보장된다.
- 고점 피벗과 저점 피벗을 **섞어서 묶는다.** 같은 가격대에서 위로 막혔다가 아래로 받쳐진
  자리는 하나의 대다. 역할은 § 4.4 가 현재가로 정한다.

### 4.3 채택 기준과 폭

```java
public record PriceZone(PriceBand band, int touches, Instant lastPivotAt) {
    Price nearEdgeFor(Direction direction);   // 롱 진입이면 상단, 숏 진입이면 하단
    Price farEdgeFor(Direction direction);
    Money width();                            // PriceBand.width() 위임
}
```

- **`touches` 가 `minTouches`(기본 2) 미만인 군집은 버린다.** 피벗 하나는 선이지 대가 아니다.
  터치 횟수가 곧 강도라는 것이 "피벗 군집" 을 고른 이유 그 자체다.
- **폭은 군집된 피벗들의 실제 `min ~ max`** 다. 별도 폭 파라미터를 두지 않는다. 허용치가
  이미 폭의 상한(`ATR × clusterMultiple × (touches−1)`)을 정하고 있어 파라미터가 겹친다.
- `PriceBand` 는 **`indicator/domain` 의 것을 재사용한다.** 두 경계와 비교하는 계산은 구름·
  볼린저와 동일하고, 경계 포함 규칙(`BandPosition.INSIDE`)이 갈리면 안 된다.
  `backtest → indicator.domain` 은 아키텍처 표에 이미 허용돼 있다.

### 4.4 역할 — 저장하지 않고 파생한다

한 번 뚫린 대는 **역할이 전환된다.** 저항이었던 자리가 지지가 된다.

이것을 상태 전이로 구현하지 않는다. 역할은 **현재 종가와 대의 위치 관계**로 매 시점 다시
정한다.

```
band.positionOf(close) == ABOVE   → 이 대는 지지 (SUPPORT)
band.positionOf(close) == BELOW   → 이 대는 저항 (RESISTANCE)
band.positionOf(close) == INSIDE  → 역할 없음. 진입 대상이 아니다
```

돌파를 감지해 플래그를 뒤집는 방식이면 "언제 뚫린 것으로 보는가"(종가? 고가? 몇 봉 유지?)
라는 두 번째 정의가 필요해지고, 그 상태가 백테스트 재실행 사이에 남으면 결정론이 깨진다.
파생값이면 그 질문 자체가 사라진다 — **지금 가격이 위면 지지, 아래면 저항.** 이것이
`ClosedTrade` 가 시각을 필드로 두지 않는 것(`docs/adr/016`)과 같은 판단이다.

대 안에 가격이 들어와 있는 동안 그 대로는 진입하지 않는다. 근단이 어느 쪽인지 정해지지 않기
때문이다.

---

## 5. 진입

### 5.1 신호

봉 `t` 의 **종가가 확정된 뒤** 판정하고, 주문은 **봉 `t+1` 부터** 유효하다.

| 방향 | 조건 | 1차 (근단) | 2차 (원단) |
|---|---|---|---|
| LONG | 종가 위쪽… 이 아니라 종가 **아래**의 최근접 지지대 | 대 **상단** | 대 **하단** |
| SHORT | 종가 **위**의 최근접 저항대 | 대 **하단** | 대 **상단** |

가격이 대로 다가오면 근단에 먼저 닿고, 더 밀리면 원단에 닿는다. **대 자체가 두 진입가를
준다** — 분할 배치에 추가 파라미터가 없다. 이것이 대를 선이 아니라 구간으로 다루는 이유다.

비중은 50 / 50 (`EntryLadder` 의 `Percentage` 두 개, 합 100%).

### 5.2 손절

```
LONG :  stopLoss = 대 하단 − ATR(t) × stopBufferMultiple
SHORT:  stopLoss = 대 상단 + ATR(t) × stopBufferMultiple
```

대가 뚫리면 진입 근거가 사라진 것이므로 그 지점이 손절이다. 손절이 시장 구조와 직결되고,
**대가 좁을수록 손절이 가까워 수량이 커진다** — `PositionPlan` 의 사이징(손절가가 수량을
결정한다)과 방향이 맞아떨어진다.

### 5.3 익절

**반대편 최근접 대의 근단.**

- LONG: 종가 위쪽 최근접 저항대의 **하단**
- SHORT: 종가 아래쪽 최근접 지지대의 **상단**

반대편 대가 없으면 **신호를 버린다.** 임의의 R 배수로 대체하지 않는다 — 그러면 전략이 두
가지 규칙을 갖게 되어 결과의 원인을 가를 수 없다.

### 5.4 게이트 — 어떤 신호를 버리는가

순서대로 검사하고, 하나라도 걸리면 그 봉의 신호는 없던 것이 된다.

1. **대 안에 가격이 있음** (§ 4.4)
2. **반대편 대 없음** (§ 5.3)
3. **`PositionPlan` 생성 거부** — 익절가가 최고 진입가보다 높지 않은 경우 등.
   반대편 대의 근단이 진입 원단보다 유리한 쪽에 있지 않으면 여기서 걸린다.
4. **지표 필터 불일치** (`indicatorFilter` 켠 경우만)
   - LONG: 일목 구름 판정이 `BELOW` 가 아니고, 볼린저 판정이 `ABOVE` 가 아닐 것
   - SHORT: 대칭
   - 두 판정 모두 `MarketContext` 와 같은 `BandPosition` 을 쓴다
5. **손익비 미달** — `plan.weakRiskReward()` 가 참이면 버린다.
   Phase 1 이 "판단은 사람이 한다" 며 경고로 남겨 둔 것을, 백테스트에서는 사람이 매번 볼 수
   없으므로 규칙으로 올린다. 문턱값 1.5 는 파라미터.
6. **`plan.analyze()` 예외** — `StopBeyondLiquidationException` / `RiskExceedsBalanceException`.
   예외를 흘려 백테스트를 중단시키지 않고 신호를 버린다. 열 수 없는 계획은 신호가 아니다.
7. **필요 증거금 초과** — `analysis.marginExceedsBalance()` 면 버린다. Phase 1 은 거부하지
   않지만(잔고 항목이 계좌 전액이 아닐 수 있으므로) 백테스트의 잔고는 정의상 전액이다.

같은 봉에 롱·숏 신호가 모두 서면 **둘 다 버린다.** 가격이 두 대 사이 어디에도 속하지 않는
상황이므로 어느 쪽을 고를 근거가 없다.

### 5.5 부분 체결

**1차만 체결된 채 가격이 달아나면 그대로 운용한다.** 2차 지정가는 포지션이 살아 있는 동안
계속 유효하고, 손절·익절은 **그 시점의 체결 수량 기준**으로 적용한다.

이것이 실제 운용과 같고, Phase 1 이 `FillState` 를 분할 체결 단계별로 들고 있는 이유이기도
하다. 반사실(`lossIfStopHonored`)과의 대조도 이 구조라야 성립한다.

---

## 6. 체결 규칙 — 봉 안에서 무슨 일이 일어났는가

OHLC 만으로는 봉 내부 경로를 알 수 없다. 그래서 **하나의 원칙으로 전부 결정한다.**

> **한 봉 안의 사건 순서는 항상 보유자에게 불리한 순서로 처리한다.**

여기서 따라 나오는 것:

| 상황 | 처리 |
|---|---|
| 손절가·익절가 둘 다 닿음 | **손절.** (인터뷰 확정) |
| 진입가·손절가 둘 다 닿음 | 진입한 뒤 손절 |
| 2차 진입가·익절가 둘 다 닿음 | 2차 진입해 평단이 나빠진 뒤 익절 |

"진입가에서 더 가까운 쪽이 먼저 닿았을 것" 같은 추정은 쓰지 않는다. 그럴듯하지만 근거가
없고, 갭으로 시작한 봉에서는 확실히 틀린 답을 낸다. 보수 가정은 **백테스트가 실제보다 좋게
나오는 것을 구조적으로 막는다** — 이 도구의 목적상 그 방향의 오차만 허용된다.

체결 판정 자체는 지정가 주문 그대로다.

```
LONG 진입 :  low  ≤ 주문가   → 체결가 = 주문가
SHORT 진입:  high ≥ 주문가   → 체결가 = 주문가
```

**청산은 갭을 반영한다.** 손절가를 뛰어넘어 시가가 열리면 체결가는 손절가가 아니라 시가다.

```
LONG 손절 :  open < stopLoss 이면 체결가 = open, 아니면 low  ≤ stopLoss 일 때 stopLoss
LONG 익절 :  open > takeProfit 이면 체결가 = open, 아니면 high ≥ takeProfit 일 때 takeProfit
```

진입은 갭을 유리하게 반영하지 않는다 — 갭으로 지나가 버린 지정가가 그 가격에 체결됐다고
보는 것은 위 원칙에 어긋난다. **갭 진입은 시가로 체결한다.**

---

## 7. 비용

```java
// backtest/domain
public record CostModel(Percentage makerFee, Percentage takerFee, Percentage slippage) {
    static CostModel binanceDefaults();   // maker 0.02% / taker 0.05% / slippage 0.02%
    static CostModel free();              // 전부 0
}
```

- **진입은 maker.** 대 경계에 미리 걸어 두는 지정가이므로 슬리피지 0.
- **청산은 taker + 슬리피지.** 손절·익절 모두 트리거 체결이다.
- 슬리피지는 체결가를 **불리한 쪽으로** 민다: 롱 청산이면 낮게, 숏 청산이면 높게.
- `free()` 를 두는 이유는 **수수료가 엣지를 먹어 치우는지를 수치로 보기 위해서**다.
  같은 스펙을 두 번 돌려 나란히 놓는다.

수수료는 명목가 기준이며 진입 회차마다 따로 계산한다. 결과는 `TradeCosts` 로 모은다 —
`journal/domain` 의 것을 재사용할지는 § 12 참조.

---

## 8. 룩어헤드 방지 — 이 Phase 의 정확성 핵심

**시점 `t` 의 판단에는 `t` 까지의 캔들만 쓴다.** 어긴 순간 백테스트는 전부 무의미해진다.
구체적으로 세 곳이 위험하다.

1. **피벗은 `t − N` 까지만 확정된다.** `i` 가 피벗인지 알려면 `i+N` 이 필요하다.
   `ZoneMap` 을 만들 때 `t` 시점에 쓸 수 있는 피벗은 `at ≤ t − N봉` 인 것뿐이다.
2. **후행스팬은 쓰지 않는다.** `IchimokuValue.laggingSpan` 은 시점 `t` 의 값에 `t+25` 봉의
   종가가 담긴다. 미래다. § 5.4 의 지표 필터는 **구름 위치와 볼린저 위치만** 본다.
   선행스팬은 25봉 전 데이터를 앞으로 민 것이라 안전하다.
3. **ATR 과 지표는 `t` 인덱스의 값**을 쓴다. `IndicatorPoint.at` 으로 맞추고 인덱스 산술로
   맞추지 않는다. 워밍업만큼 앞이 잘려 두 리스트의 인덱스가 어긋나기 때문이다.

**이것을 테스트로 강제한다** — § 10 의 접미사 불변 테스트가 그 장치다.

시작 인덱스는 세 워밍업의 최댓값 이후다: 일목 77봉, ATR `period+1`, 피벗 `2N+1` 에 확정
지연 `N` 을 더한 것.

---

## 9. 실행과 결과

### 9.1 스펙

Checkstyle 파라미터 4개 한계 때문에 설정을 두 단으로 묶는다.

```java
public record ZoneSettings(int pivotLookback, BigDecimal clusterMultiple,
                           int minTouches, int atrPeriod) {
    static ZoneSettings standard();       // 5 / 0.5 / 2 / 14
}

public record EntryRules(BigDecimal stopBufferMultiple, BigDecimal minRiskReward,
                         boolean indicatorFilter) {}

public record StrategySettings(ZoneSettings zones, EntryRules rules) {}

public enum CapitalMode { COMPOUND, FIXED }

public record AccountSettings(Money initialCapital, Percentage riskPercent,
                              int leverage, CapitalMode capitalMode) {
    Money balanceFor(Money currentEquity);   // FIXED 면 initialCapital, COMPOUND 면 인자
}

public record BacktestSpec(CandleQuery query, StrategySettings strategy,
                           AccountSettings account, CostModel costs) {}
```

기본 파라미터 값은 구현 중 확정한다. 위 숫자는 출발점이지 결론이 아니다.

### 9.2 잔고

**복리와 고정 둘 다 지원한다** (`CapitalMode`).

- `FIXED` — 매 거래 `initialCapital × riskPercent`. 거래 간 독립이라 전략 자체의 엣지가
  깨끗하게 보이고, 거래당 손익을 서로 비교할 수 있다.
- `COMPOUND` — 직전 거래까지의 자산에 비율 적용. 실사용과 같은 곡선.

두 모드를 함께 두는 이유는 Phase 2 가 이미 복리 곡선을 다루고 있어 **대조가 가능하기**
때문이다. 백테스트가 낸 승률·손익비를 `ProjectionSpec` 에 넣었을 때 나오는 분포와, 실제
캔들 위에서 나온 곡선이 얼마나 다른지가 Phase 2 의 "거래 내부 왕복이 모델에 없어 낙폭이
실제보다 얕다" 는 한계를 수치로 확인해 준다.

### 9.3 결과

```java
public record SimulatedTrade(
        Direction direction, PositionPlan plan, ExecutedEntries entries,
        Exit exit, ExitReason reason, TradeCosts costs, MarketContext context) {
    Money realizedPnl();  Money lossIfStopHonored();  Duration holdingPeriod();
}

public record BacktestResult(
        BacktestSpec spec, List<SimulatedTrade> trades, EquityCurve equity) {
    int totalTrades();      Percentage winRate();
    BigDecimal profitFactor();                 // 총이익 / 총손실
    BigDecimal averageRiskReward();
    Percentage maxDrawdown();                  // EquityCurve 위임
    Money finalEquity();
}
```

`ExitReason` 은 `journal/domain` 의 것과 의미가 같다 — `PLANNED_STOP` / `PLANNED_TARGET` /
`LIQUIDATED`. 백테스트는 계획을 어기지 않으므로 `MANUAL_EARLY` / `HELD_PAST_STOP` 은 나오지
않는다. **재사용 여부는 § 12.**

`LIQUIDATED` 는 실제로 발생하지 않아야 한다 — § 5.4 게이트 6 이 손절이 청산가 안쪽임을
보장하기 때문이다. 그럼에도 판정을 넣는 이유는 **그 불변식이 깨지면 알아야 하기** 때문이다.

### 9.4 노출

`backtest/api` 에 REST 엔드포인트. 요청은 `BacktestSpec` 에 대응하는 DTO, 응답은
`BacktestResult` 요약 + 자산 곡선 점 목록. `@Schema` 는 conventions.md 규칙대로.

---

## 10. 검증

하네스 우선. 아래는 **구현 전에 이름부터 쓴다.**

### 10.1 결정론 (완료 조건)

```
동일한_스펙을_두_번_실행하면_결과가_완전히_같다()
```

`BacktestResult` 전체를 `equals` 로 비교한다. 지표 요약만 비교하면 체결 하나가 달라도
통과할 수 있다. 난수는 쓰지 않으므로 시드 고정 항목은 없다.

### 10.2 룩어헤드 (§ 8 강제)

```
캔들을_뒤에서_잘라도_남은_구간의_거래가_동일하다()
```

전체 구간으로 한 번, 마지막 K봉을 떼고 한 번 돌려 **겹치는 구간의 거래 목록이 같은지**
본다. 미래를 한 번이라도 참조하면 앞 구간의 판단이 달라진다. 피벗 확정 지연이나 후행스팬
사용 같은 실수를 특정 케이스가 아니라 **모든 입력에 대해** 잡는다. Phase 4 가 Pine 소스
대조로 얻은 것과 같은 종류의 증거다.

### 10.3 봉 안의 순서

```
같은_봉에서_손절과_익절이_모두_닿으면_손절로_처리한다()
갭으로_손절가를_뛰어넘어_열리면_체결가는_시가다()
같은_봉에서_진입과_손절이_모두_닿으면_진입한_뒤_손절한다()
```

### 10.4 대와 역할

```
피벗이_하나뿐인_군집은_대로_채택되지_않는다()
종가가_대_위에_있으면_지지_아래에_있으면_저항이다()
가격이_대_안에_있는_동안에는_그_대로_진입하지_않는다()
군집_결과는_피벗_입력_순서에_의존하지_않는다()
```

### 10.5 ATR golden test

진폭이 일정한 합성 캔들에서 ATR 이 그 진폭과 같다. 트레이딩뷰 Pine 소스와의 대조 결과를
ADR 에 원문으로 인용한다.

### 10.6 부분 체결

```
일차만_체결된_상태에서_손절되면_손실은_일차_수량_기준이다()
이차_지정가는_포지션이_열려_있는_동안_계속_유효하다()
```

### 10.7 아키텍처 (완료 조건)

- **규칙 5 의 `allowEmptyShould(true)` 를 제거한다. 이것이 마지막 플래그다.**
  제거 후 `ArchitectureRules` 에 `allowEmptyShould` 가 한 건도 남지 않아야 한다.
  위반 픽스처 `archfixture/r5` 는 이미 있다 — 실제 `backtest` 패키지가 생긴 뒤에도 규칙이
  발동하는지 `ArchitectureRulesViolationTest` 로 확인한다.
- **"`backtest` 에 바이낸스 관련 코드가 한 줄도 없다"** 는 규칙 5 로 강제된다.
  문자열 `binance` 가 `backtest` 소스에 없는지도 함께 본다 — 주석과 상수까지 잡기 위해서다.

### 10.8 커버리지

`backtest/domain` 90% (JaCoCo 게이트). `backtest/api` 는 강제 대상 아님.

---

## 11. 새로 생기는 의존

아키텍처 표에 이미 있는 것:

```
backtest → indicator, position, market.application.port.out, market.domain
```

`PriceBand` / `BandPosition` 재사용은 `indicator.domain` 이라 그대로 허용된다.

**표에 없어서 추가해야 하는 것:**

| 추가 | 무엇을 위해 | 판단 |
|---|---|---|
| `backtest → projection.domain` | `EquityCurve` (`maxDrawdown` 포함) | § 12 |
| `backtest → journal.domain` | `ExecutedEntries` · `Exit` · `TradeCosts` · `ExitReason` · `MarketContext` | § 12 |

`common/domain` 에는 메서드가 하나 는다. 대 경계에 ATR 버퍼를 더하려면 필요한데 지금 없다.

```java
Price.plus(Money)    // 손절 버퍼, 슬리피지
Price.minus(Money)
```

`Price` 는 음수를 금지하므로 `minus` 가 0 아래로 내려가면 던진다. 손절가가 0 이하로 나오는
계획은 어차피 성립하지 않으니 그 편이 맞다.

---

## 12. 열어 둔 것 — 구현 시작 전에 결정한다

### 12.1 `journal/domain` 타입 재사용 여부

`SimulatedTrade` 가 필요로 하는 `ExecutedEntries` · `Exit` · `TradeCosts` · `ExitReason` ·
`MarketContext` 는 **`journal/domain` 에 이미 정확히 같은 의미로 있다.** conventions.md 는
"같은 로직이 두 번째 나타나면 즉시 추출한다" 고 못박고 있다.

그런데 아키텍처 표는 `journal → position, indicator.domain` 만 허용하고 역방향
`backtest → journal` 은 없다. 선택지는 셋이다.

1. **`backtest → journal.domain` 을 표에 추가한다.** 가장 적은 코드. 다만 백테스트가 매매
   기록 모듈에 의존하는 그림이 되어 "기록" 과 "시뮬레이션" 의 경계가 흐려진다.
2. **다섯 타입을 `common/domain` 으로 올린다.** `Candle` 을 올리지 않은 이유(`docs/adr/013`)
   와 정확히 같은 문제가 생긴다 — `common` 이 반올림 정책을 가진 값 객체 모음에서 공용 모델
   전반으로 넓어진다.
3. **`backtest` 에 따로 정의한다.** 의존은 깨끗하지만 `ExitReason.honorsPlan()` 같은 규칙이
   두 곳에 생긴다. 계산 도메인에서 같은 규칙이 갈라지는 것을 가장 경계해 왔다.

**잠정 판단은 1 이다.** `journal.domain` 은 프레임워크 의존이 없는 순수 도메인이고, 실제
매매 기록과 백테스트 결과가 **같은 어휘로 표현되는 것 자체가 값**이다 — 백테스트로 검증한
전략과 실제로 한 매매를 나란히 놓고 볼 수 있다. `EquityCurve`(`projection.domain`)도 같은
논리다. 확정되면 **ADR 018** 로 근거를 남기고 architecture.md 의존 표를 갱신한다.

### 12.2 `MarketContext.rationale` 을 백테스트가 무엇으로 채우는가

이 필드는 비면 거부한다(`docs/adr/017`). 사람이 쓰는 자유 텍스트를 전제한 것이라 백테스트가
채울 값이 명확하지 않다. 대 경계와 터치 횟수를 문장으로 찍는 것이 후보다.

**여기서 확인되는 것:** `MarketContext` 의 지지·저항을 Phase 5 에서 구조화하지 않고 미룬
판단이 옳았다. 이제 대의 정의가 확정됐으므로 `PriceZone` 을 참조하는 구조화된 필드를 넣을
수 있다 — 다만 그것은 `journal → backtest` 라는 새 방향을 만드는 별개의 결정이므로
**Phase 6 범위 밖으로 둔다.**

### 12.3 파라미터 기본값

§ 9.1 의 숫자는 출발점이다. `pivotLookback` · `clusterMultiple` · `stopBufferMultiple` 의
기본값은 구현 후 실제 캔들로 몇 번 돌려 보고 정한다. **다만 값을 맞추려고 과최적화하지
않는다** — 이 도구의 목적은 좋은 성적표가 아니라 재현 가능한 측정이다.

---

## 13. 구현 순서

각 단계는 앞 단계의 테스트가 초록인 상태에서 시작한다.

1. `Price.plus/minus(Money)` — `common/domain`
2. `AverageTrueRange` — `indicator/domain`, Pine 소스 대조 + golden test
3. `Pivot` · `PivotDetector` — `backtest/domain`
4. `PriceZone` · `ZoneMap` · 군집 — 역할 파생 포함
5. **규칙 5 `allowEmptyShould` 제거** (여기서 `backtest` 패키지가 생기므로)
6. 신호 생성 → `PositionPlan` — 게이트 7종
7. 체결 엔진 — 봉 안의 순서, 갭, 부분 체결
8. 비용 · 잔고 모드 · `BacktestResult`
9. 룩어헤드 접미사 불변 테스트 (§ 10.2)
10. `backtest/api` + Swagger
11. ADR 018, architecture.md · domain-model 스킬 갱신
