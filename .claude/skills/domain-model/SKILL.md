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

```java
public enum Direction { LONG, SHORT }

public record PlannedEntry(Price price, Percentage allocation) {}

public record PositionPlan(
    Direction direction,
    List<PlannedEntry> entries,     // 50% 분할 → 2건
    Price stopLoss,
    Price takeProfit,
    int leverage
) {
    Price averageEntryPrice();
    Price averageEntryPriceIfPartial(int filledCount);
    Quantity totalQuantity(Money riskAmount);
    Money requiredMargin(Money accountBalance);
    Price liquidationPrice();
    Price liquidationPriceIfPartial(int filledCount);
    Money maxLossIfAllFilled(Money accountBalance);
    BigDecimal riskRewardRatio();
}
```

### 불변 규칙 (각각 테스트로 표현)

1. 롱은 손절가 < 최저 진입가, 숏은 손절가 > 최고 진입가
2. 분할 비중의 합은 정확히 100%
3. 1차만 체결된 상태와 전량 체결 상태의 청산가는 다르다
4. **전량 체결 시 최대 손실은 1차 진입 시점에 계산 가능해야 한다**
5. 손절가가 청산가 너머면 `StopBeyondLiquidationException`
6. 리스크 금액이 계좌 초과 시 거부
7. 손익비 1.5 미만이면 경고 플래그 (거부 아님)

규칙 4가 이 모듈의 존재 이유다. 분할매수는 "아직 여유 있다"는 착각을 만들기 때문에, 1차 진입 시점에 최종 리스크가 보여야 한다.

### 사이징 계산 순서

```
riskAmount = balance × riskPercent
perUnit    = |avgEntry - stopLoss|
quantity   = riskAmount / perUnit
notional   = quantity × avgEntry
margin     = notional / leverage
```

사이즈가 먼저가 아니라 **손절가가 수량을 결정한다.**

### 청산가

초기 구현은 유지증거금률(MMR) 0.4% 고정 근사치.
`MaintenanceMarginPolicy` 인터페이스로 추상화하고 Phase 3에서 `leverageBracket` 기반 구현체로 교체.

```
LONG:  liq = entry × (1 - 1/leverage + MMR)
SHORT: liq = entry × (1 + 1/leverage - MMR)
```

분할 진입 시 `entry`는 해당 시점의 가중 평단가.

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
