---
name: domain-model
description: CoinWin 도메인 모델 명세. 포지션 사이징, 분할 진입, 청산가, 일목·볼린저 지표, 매매 기록 구조를 구현하거나 수정할 때 참조한다.
---

# 도메인 모델

## 값 객체 (`common/domain`)

```java
public record Price(BigDecimal value)       // 스케일 2, HALF_UP, 음수 금지
public record Quantity(BigDecimal value)    // 스케일 8 (BTC)
public record Money(BigDecimal value)       // USDT, 스케일 2
public record Percentage(BigDecimal value)  // 스케일 4
```

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

초기 구현은 유지증거금률(MMR) 0.4% 고정 근사치.
`MaintenanceMarginPolicy` 인터페이스로 추상화하고 Phase 3에서 `leverageBracket` 기반 구현체로 교체. 근거는 `docs/adr/008`.

```
LONG:  liq = entry × (1 - 1/leverage + MMR)
SHORT: liq = entry × (1 + 1/leverage - MMR)
```

분할 진입 시 `entry`는 해당 시점의 가중 평단가.

**MMR 은 테스트가 주입한다.** 고정 근사치 0.4% 를 청산가 테스트에 하드코딩하지 않는다. Phase 3 에서 구간별 MMR 로 바뀌는 순간 테스트가 한꺼번에 깨지고, 깨진 이유가 "공식이 틀렸다" 인지 "MMR 입력이 달라졌다" 인지 구분되지 않는다.

### `BigDecimal` 노출

`riskRewardRatio()` 만 `BigDecimal` 을 반환한다. 손익비는 무차원 비(比)라 `Percentage` 도 `Money` 도 아니기 때문이다. 그 외 도메인 반환값은 전부 값 객체다.

## 지표 (`indicator/domain`)

```java
public record Candle(Instant openTime, Price open, Price high,
                     Price low, Price close, BigDecimal volume) {}

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

바이낸스 공개 엔드포인트만 사용. API 키 불필요.

| 용도 | 엔드포인트 |
|---|---|
| 캔들 | `/fapi/v1/klines` |
| 펀딩비 | `/fapi/v1/premiumIndex` |
| 미결제약정 | `/fapi/v1/openInterest`, `/futures/data/openInterestHist` |
| 롱숏 비율 | `/futures/data/globalLongShortAccountRatio` |
| 레버리지 구간 | `/fapi/v1/leverageBracket` |

Rate limit, 캐싱, 재시도는 `adapter/out/binance`에 격리. 도메인은 HTTP를 모른다.
