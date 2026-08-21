---
name: domain-model
description: CoinWin 도메인 모델 명세. 포지션 사이징, 분할 진입, 청산가, 복리·몬테카를로, 일목·볼린저 지표, 매매 기록 구조를 구현하거나 수정할 때 참조한다.
---

# 도메인 모델

## 값 객체 (`common/domain`)

```java
public record Price(BigDecimal value)       // 스케일 2, HALF_UP, 음수 금지
public record Quantity(BigDecimal value)    // 스케일 8 (BTC)
public record Money(BigDecimal value)       // USDT, 스케일 2
public record Percentage(BigDecimal value)  // 스케일 4
```

계산 메서드는 값 객체 안에만 둔다. 반올림 정책이 밖으로 나가면 결과가 갈라진다.

```java
Money.dividedBy(Money) → Quantity        // 사이징: riskAmount / perUnitLoss
Money.times(BigDecimal) → Money          // 무차원 배수. 복리 곡선의 한 점
Money.minus(Money) → Money               // 음수 허용
Money.percentOf(Money) → Percentage      // 낙폭: (고점 - 현재) / 고점
Percentage.applyTo(Money|Quantity)       // 비율 적용
Percentage.ofRatio(long, long)           // 개수의 비율. 손실 시행 / 전체 시행
Percentage.asFraction() → BigDecimal     // 100 기준 → 1 기준
Price.absoluteDifference(Price) → Money
Price.multipliedBy(BigDecimal) → Price   // 청산가 계수
Quantity.times(Money[, int parts]) → Money
```

`DomainValues.required(value, label)` 로 null 을 검사한다. `Objects.requireNonNull` 은
`NullPointerException` 을 던져 500 이 되고, 잘못된 요청은 400 이어야 한다.

## 포지션 (`position/domain`)

> Phase 1 구현 완료. 아래는 실제 코드와 일치한다.

```java
public enum Direction { LONG, SHORT }

public record PlannedEntry(Price price, Percentage allocation) {}

// 분할 진입 계획 전체. 순서가 체결 순서다. 가중 평단 계산을 소유한다.
public record EntryLadder(List<PlannedEntry> entries) {
    int size();
    Price averagePriceAfter(int filledCount);         // 체결된 회차만으로 다시 가중
    Percentage allocationFilledAfter(int filledCount);
    Price lowestPrice();
    Price highestPrice();
}

// 이 거래 한 건에 걸 수 있는 돈. 금액이 아니라 잔고 + 비율로 받는다.
public record RiskBudget(Money accountBalance, Percentage riskPercent) {
    Money riskAmount();                               // balance × riskPercent
}

public record PositionPlan(
    Direction direction,
    EntryLadder entries,            // 50% 분할 → 2건
    Price stopLoss,
    Price takeProfit,
    int leverage
) {
    Price averageEntryPrice();
    Price averageEntryPriceIfPartial(int filledCount);
    Quantity totalQuantity(RiskBudget budget);
    Money requiredMargin(RiskBudget budget);
    BigDecimal riskRewardRatio();
    PositionAnalysis analyze(RiskBudget budget, MaintenanceMarginPolicy policy);
}

// n 건까지 체결됐을 때의 상태. 청산가와 최대손실이 여기 붙는다.
public record FillState(
    int filledEntries,
    Price averageEntryPrice,
    Quantity quantity,
    Price liquidationPrice,
    Money maxLoss
) {}

public record PositionAnalysis(
    List<FillState> fillStates,     // 1건 체결 → 전량 체결, 순서대로
    Money requiredMargin,
    Money accountBalance,
    BigDecimal riskRewardRatio,     // 표시용. 버림, 스케일 2
    boolean weakRiskReward          // 판정은 계획이 한다. 여기서 재계산하지 않는다
) {
    FillState afterFirstEntry();
    FillState whenFullyFilled();
    boolean marginExceedsBalance();
}
```

### 청산가·최대손실은 계획이 아니라 체결 상태의 속성이다

근거는 `docs/adr/009`. `PositionPlan.liquidationPrice()` 는 두지 않는다. **분할 진입 계획에는 단일 청산가가 존재하지 않기 때문이다.** 1차만 체결된 포지션과 전량 체결된 포지션은 평단이 다르고 따라서 청산가도 최대손실도 다른 값이다. 계획에 하나를 매달면 둘 중 하나가 사라진다.

접근 경로는 `plan.analyze(budget, policy).afterFirstEntry().liquidationPrice()` 다. `fillStates` 는 1건 체결부터 전량까지 순서대로 담기므로 `filledCount` 를 인자로 받는 별도 메서드가 필요 없다.

### 사이징 입력은 금액이 아니라 `RiskBudget` 이다

`totalQuantity(Money riskAmount)` 처럼 금액만 받으면 규칙 6(리스크 금액이 계좌 초과 시 거부)을 검사할 수 없다. 잔고 대비 몇 퍼센트를 걸고 있는지가 계획 검토에서 실제로 보고 싶은 값이기도 하다.

### 불변 규칙 (각각 테스트로 표현)

**거부 — `PositionPlan` 생성 시점**

1. 롱은 손절가 < 최저 진입가, 숏은 손절가 > 최고 진입가
2. 분할 비중의 합은 정확히 100%
3. 롱은 익절가 > 최고 진입가, 숏은 익절가 < 최저 진입가
   — 없으면 익절가가 반대편에 있는 계획에서 `riskRewardRatio()` 가 절대값 탓에 멀쩡한 양수를 돌려준다. 조용히 틀린 값이 나가는 구멍이다.

**거부 — `analyze()` 시점**

4. 손절가가 청산가 너머면 `StopBeyondLiquidationException`
   — **모든 체결 상태에서 검사한다.** 전량 체결 기준으로만 보면 1차 체결 상태에서만 청산이 손절보다 앞서는 계획을 놓친다. 평단이 불리한 쪽은 대개 부분 체결 상태다.
5. 리스크 금액이 계좌 초과 시 `RiskExceedsBalanceException` (`RiskBudget` 생성 시점)

**경고 — 거부하지 않는다. 판단은 사람이 한다**

6. 손익비 1.5 미만이면 `weakRiskReward()`
   — 비교는 **반올림하지 않은 손익비**로 한다. 그리고 표시용 `riskRewardRatio()` 는 **버림**이다. 반올림하면 실제 1.495 가 1.50 으로 표시되어, 경고는 켜져 있는데 화면에는 기준을 넘은 것처럼 보인다. 버림이면 "표시값 1.50 이상"과 "기준 충족"이 정확히 일치한다.
7. 필요 증거금이 잔고 초과면 `marginExceedsBalance()`
   — 손절이 좁을수록 수량이 커지고 증거금이 커진다. 리스크 16 USDT 짜리 계획의 필요 증거금이 959 USDT 가 되는 일이 실제로 생긴다. 잔고 항목이 계좌 전액이 아닐 수 있으므로 거부하지 않는다.

**성질 — 산출 결과가 드러내야 하는 것 (강제 대상 아님)**

8. **전량 체결 시 최대 손실은 1차 진입 시점에 계산 가능해야 한다**

규칙 8이 이 모듈의 존재 이유다. 분할매수는 "아직 여유 있다"는 착각을 만들기 때문에, 1차 진입 시점에 최종 리스크가 보여야 한다. 사이징 정의상 `whenFullyFilled().maxLoss() == budget.riskAmount()` 가 성립한다.

### 폐기된 규칙 — "1차와 전량의 청산가는 다르다"

구 명세에 있던 이 항목은 **불변식이 아니다.** 같은 가격 2분할(60000/60000)이면 두 평단이 같고 따라서 두 청산가도 같다. 그리고 같은 가격에 주문을 나눠 거는 것은 체결 확률과 슬리피지를 분산하려는 실제 운용이므로, 거부하지 않는다.

청산가가 갈리는 것은 **진입가가 다를 때 따라 나오는 결과**이지 계획이 만족해야 할 조건이 아니다. 강제하려 들면 정상적인 계획을 막는다.

### 사이징 계산 순서

```
riskAmount = balance × riskPercent
perUnit    = |avgEntry - stopLoss|
quantity   = riskAmount / perUnit
notional   = quantity × avgEntry
margin     = notional / leverage
```

사이즈가 먼저가 아니라 **손절가가 수량을 결정한다.**

부분 체결 수량은 이 총수량에 체결 비중을 적용해 얻는다. 부분 체결 시점에 다시 사이징하지 않는다 — 그러면 회차별 수량의 합이 총수량과 어긋난다.

`margin` 은 **명목가를 레버리지로 나눈다.** 명목가를 `Money` 스케일 2 로 스냅한 뒤 레버리지 역수를 곱하면 이중 반올림으로 1센트가 어긋난다.

### 청산가

> Phase 3 에서 근사식을 버리고 거래소 정확식으로 교체했다. 근거는 `docs/adr/012`.

```
LONG :  liq = [entry × (1 - 1/leverage) - deduction/qty] / (1 - MMR)
SHORT:  liq = [entry × (1 + 1/leverage) + deduction/qty] / (1 + MMR)
```

**나눗셈인 이유**는 유지증거금이 진입 명목가가 아니라 **청산가 기준 명목가**(`qty × P × MMR`)로 계산되기 때문이다. 미지수가 양변에 있어 정리하면 나눗셈이 남는다. Phase 1 의 근사식 `entry × (1 - 1/lev + MMR)` 은 그것을 곱셈 한 번으로 뭉갠 것이었고, EP 60,000 / 10배에서 23.13 USDT (0.043%) 어긋났다.

분할 진입 시 `entry`는 해당 시점의 가중 평단가.

```java
// position/domain
public record MaintenanceMargin(Percentage rate, Money deduction) {}

public interface MaintenanceMarginPolicy {
    MaintenanceMargin requirementFor(Money notional);   // 인자가 명목가인 이유: 구간별 MMR
}

// 청산가는 이 넷과 유지증거금 규칙만으로 결정된다
public record PositionExposure(
        Direction direction, Price averageEntryPrice, Quantity quantity, int leverage) {
    Money notional();
    Price liquidationPrice(MaintenanceMargin margin);
}
```

`deduction`(유지증거금 공제액)은 구간 경계에서 유지증거금이 끊기지 않게 하는 이음매다. 구간 선택은 **진입 명목가**로 한다 — 거래소는 청산가 기준 명목가로 고르므로 경계 근처에서 미세하게 갈릴 수 있고, 그 한계를 ADR 012 에 적어 두었다.

**MMR 은 테스트가 주입한다.** 도메인 테스트는 `FixedMaintenanceMarginPolicy` 로 값을 직접 넣는다. 구간표를 끌고 들어오면 공식이 틀렸는지 구간 선택이 틀렸는지 구분되지 않는다. 조립 전체(스냅샷 → 어댑터 → 서비스 → 정책 → 공식)는 `LiquidationAgreesWithExchangeTest` 가 따로 검사한다.

### `BigDecimal` 노출

`riskRewardRatio()` 만 `BigDecimal` 을 반환한다. 손익비는 무차원 비(比)라 `Percentage` 도 `Money` 도 아니기 때문이다. 그 외 도메인 반환값은 전부 값 객체다.

## 복리 / 몬테카를로 (`projection/domain`)

> Phase 2 구현 완료. 아래는 실제 코드와 일치한다.

```java
public enum TradeOutcome { WIN, LOSS }

// 승률·손익비·거래당 리스크 비율. 이 셋이 자산 곡선의 모양을 전부 결정한다.
public record TradingEdge(
        Percentage winRate, BigDecimal riskRewardRatio, Percentage riskPerTrade) {
    BigDecimal factorFor(TradeOutcome outcome);   // 승리 1 + r×R, 패배 1 - r
    BigDecimal expectancyPerTrade();              // R 배수 기댓값: 승률×R - 패률
    TradeOutcome drawOutcome(RandomGenerator random);
}

public record TradeFrequency(int tradesPerWeek, int weeks) {
    int totalTrades();                            // 상한 10,000
}

// 승패 순서 하나가 그리는 곡선. 첫 점은 거래 이전의 초기 자본이다.
public record EquityCurve(List<Money> points) {
    Money initialCapital();  Money finalEquity();
    int trades();            Percentage maxDrawdown();
    boolean lostMoney();
}

public record ProjectionSpec(Money initialCapital, TradingEdge edge, TradeFrequency frequency) {
    EquityCurve project(List<TradeOutcome> outcomes);   // 확정된 순서
    EquityCurve simulate(long seed);                    // 시드가 정한 순서
}

public record MonteCarloProjection(ProjectionSpec spec, int runs, long seed) {
    ProjectionDistribution run();                       // 시행 상한 10,000
}

public record ProjectionOutcome(Money finalEquity, Percentage maxDrawdown) {}

public record ProjectionDistribution(List<ProjectionOutcome> outcomes, Money initialCapital) {
    int runs();
    Money equityPercentile(int percentile);        // 0 최악, 50 중앙값, 100 최선
    Percentage drawdownPercentile(int percentile); // 낙폭 기준으로 따로 정렬한다
    Percentage lossProbability();                  // 초기 자본에 못 미친 시행의 비율
}
```

### 순서는 최종 자산이 아니라 낙폭을 바꾼다

근거는 `docs/adr/010`. 고정 비율 복리는 곱셈이고 곱셈은 교환법칙을 따른다. 승패 구성이
같으면 순서가 어떻든 최종 자산이 같다. `[승,패,패,승]` 과 `[패,승,승,패]` 는 둘 다 1166.40 이고,
갈리는 것은 낙폭 19% 대 10% 다. **그래서 분포에 최종 자산과 낙폭을 함께 싣는다.**
사람이 계획을 그만두는 지점은 도착점이 아니라 낙폭이기 때문이다.

### 각 점은 직전 점이 아니라 초기 자본에서 계산한다

`points[i] = initialCapital.times(누적 배수)` 다. 직전 점에 배수를 곱해 나가면 점마다 센트
반올림이 끼고, 5 거래 만에 1센트가 어긋난다(1159.28 대 1159.27). 그 오차가 쌓이면 순서만
다른 두 경로의 최종 자산이 갈라지는데, 그것은 복리의 성질이 아니라 반올림의 잔재다.
누적 배수는 `MathContext.DECIMAL128`, `Money` 스냅은 각 점당 한 번.

### 결정론

`SeededRandom` 이 알고리즘 이름(`L64X128MixRandom`)을 박아 둔다. 기본 구현에 맡기면 JDK 가
바뀔 때 같은 시드가 다른 수열을 내고, 어제 본 시뮬레이션을 오늘 다시 만들 수 없다.

승패 추첨은 `random.nextInt(1_000_000) < 승률×1_000_000` 이다. 실수 난수 비교는 경계에서
구현에 따라 갈릴 수 있다. 해상도 백만은 `Percentage` 스케일 4 의 최소 단위와 맞춘 것이다.

**몬테카를로는 난수원 하나를 모든 시행이 이어 쓴다.** 시행마다 새로 만들면 같은 시드에서
같은 수열이 다시 시작되어 N 번이 전부 같은 경로가 된다.

### 하지 않는 것

- **파산 확률을 내지 않는다.** 고정 비율 사이징에서 자산은 산술적으로 0 이 되지 않아
  임의의 문턱값을 도입해야 한다. 대신 손실 확률과 최악 낙폭을 낸다.
- **곡선을 시행마다 보관하지 않는다.** 분포에 필요한 것은 최종 자산과 낙폭뿐이다.
  곡선을 다시 보고 싶으면 그 시드로 `simulate(seed)` 를 다시 부른다 — 결정론이라 같은 곡선이 나온다.
- 거래 내부의 왕복(손절가까지 밀렸다 익절로 끝나는 경우)은 모델에 없다. 여기서 나오는 낙폭은
  **실제보다 얕다.** 거래 내부를 보려면 캔들이 필요하고 그것은 Phase 6 이다.

## 지표 (`indicator/domain`)

> `Candle` 은 `indicator` 가 아니라 **`market/domain`** 에 있다. `market` 이 생산하고
> `indicator`·`backtest` 가 소비하는 공통 어휘이며, `indicator` 에 두면 `market → indicator`
> 라는 더 나쁜 방향이 생긴다. 근거는 `docs/adr/013`.

```java
public record IchimokuValue(
    Price conversionLine,   // 전환선 9
    Price baseLine,         // 기준선 26
    Price leadingSpanA,     // 선행스팬1
    Price leadingSpanB,     // 선행스팬2 52
    Price laggingSpan       // 후행스팬
) {
    CloudPosition positionOf(Price price);  // ABOVE / INSIDE / BELOW
}

public record BollingerValue(Price upper, Price middle, Price lower) {
    Percentage bandWidth();
    BandPosition positionOf(Price price);
}
```

계산기는 `List<Candle>` → `List<IndicatorValue>` 순수 함수. 상태 없음.
워밍업 구간(일목 52개 미만) 처리 필수. 캔들 부족 시 예외.

## 매매 기록 (`journal/domain`)

```java
public record TradeRecord(
    TradeId id,
    PositionPlan plan,
    List<Fill> fills,
    ExitReason exitReason,
    Money realizedPnl,
    MarketContext contextAtEntry,   // 진입 시점 지지·저항·지표 상태
    Instant openedAt,
    Instant closedAt
) {
    boolean followedPlan();
    Money lossIfStopHonored();      // 반사실: 손절 지켰다면
    Duration timeSincePreviousTrade(TradeRecord previous);
}

public enum ExitReason {
    PLANNED_STOP, PLANNED_TARGET, MANUAL_EARLY, HELD_PAST_STOP, LIQUIDATED
}
```

`followedPlan()`과 `lossIfStopHonored()`가 이 모듈의 핵심이다.
**손익과 계획 준수 여부를 분리해서 집계할 수 있어야 한다.** 규칙을 지키고 진 거래와 규칙을 어기고 이긴 거래는 다른 데이터다.

## 시장 (`market/domain`)

> Phase 3 구현 완료. 아래는 실제 코드와 일치한다.

```java
public record Symbol(String value)                  // 대문자 영숫자. 저장 키의 일부
public enum CandleInterval { ONE_MINUTE("1m", …), … }  // code() 는 질의 파라미터이자 저장 키
public record TimeRange(Instant from, Instant to)   // 반열림 [from, to)
public record CandleQuery(Symbol symbol, CandleInterval interval, TimeRange range)

public record Candle(Instant openTime, Price open, Price high,
                     Price low, Price close, Quantity volume) {}

// 시간 오름차순 + 같은 시각 1회. 정렬·중복 제거를 하지 않고 거부한다
public record CandleSeries(List<Candle> candles) {
    CandleSeries within(TimeRange range);
    CandleSeries merge(CandleSeries other);   // 겹치면 인자 쪽이 이긴다 (거래소가 정정해 준다)
}

public record FundingRate(BigDecimal value)   // 백분율, 스케일 6, 음수 허용
public record MarketMetrics(Symbol symbol, Instant at, FundingRate fundingRate,
                            Quantity openInterest, BigDecimal longShortRatio) {}

public record LeverageBracket(int tier, Money notionalCap,
                              Percentage maintenanceMarginRate, Money maintenanceAmount) {}
public record LeverageBrackets(Symbol symbol, List<LeverageBracket> brackets) {
    LeverageBracket forNotional(Money notional);   // 상한 포함. 넘으면 예외
}
```

`Candle.volume` 이 `BigDecimal` 이 아니라 `Quantity` 인 이유는 klines 의 volume 이 기초자산(BTC) 수량이기 때문이다. 스케일 8 이 정확히 맞는다.

`FundingRate` 가 `Percentage` 가 아닌 이유는 **음수가 정상값**이기 때문이다. 숏이 우세하면 숏이 롱에게 낸다. `Percentage` 는 음수를 금지하고, 부호를 잃으면 "누가 누구에게 내는가" 가 사라진다.

`LeverageBrackets` 는 생성 시점에 **구간 경계에서 유지증거금이 연속인지** 검사한다: `a(i+1) = a(i) + C × (r(i+1) - r(i))`. 이 표가 조용히 틀리면 청산가가 조용히 틀린다.

### 엔드포인트

| 용도 | 엔드포인트 | 키 |
|---|---|---|
| 캔들 | `/fapi/v1/klines` | 불필요 |
| 펀딩비 | `/fapi/v1/premiumIndex` | 불필요 |
| 미결제약정 | `/fapi/v1/openInterest` | 불필요 |
| 롱숏 비율 | `/futures/data/globalLongShortAccountRatio` | 불필요 |
| 레버리지 구간 | ~~`/fapi/v1/leverageBracket`~~ | **HMAC 서명 필요 (401)** |

**`leverageBracket` 은 공개 엔드포인트가 아니다.** 서명 없이 부르면
`{"code":-2014,"msg":"API-key format invalid."}` 가 돌아온다. `scope.md` 전제상 API 키를 쓰지
않으므로 구간표는 커밋된 스냅샷(`resources/market/btcusdt-leverage-bracket.json`)에서 읽고,
갱신은 파일 교체다. 손상되면 위 연속성 검사가 잡는다.

### 캔들 조회와 수집은 분리한다

```java
LoadMarketDataUseCase.candles(query)   // 저장된 것만 읽는다. 거래소를 때리지 않는다
SyncMarketDataUseCase.sync(query)      // 거래소에서 받아 증분 저장. 새로 저장된 수를 돌려준다
```

조회가 매번 거래소를 때리면 같은 질의가 같은 답을 내지 않아 Phase 6 백테스트의 "동일 파라미터 재실행 시 결과 완전 동일" 이 그 자리에서 무너진다. 네트워크가 끊겼을 때 이미 저장해 둔 것마저 못 읽는 문제도 있다.

`LoadCandlesPort` 구현체는 셋이고 **하나의 계약 스위트를 셋 모두 통과**한다. Rate limit, 페이지 이어받기, 구간 경계 변환(바이낸스 `endTime` 은 포함 경계라 1ms 를 뺀다)은 `adapter/out/binance` 에 격리. 도메인은 HTTP 를 모른다.
