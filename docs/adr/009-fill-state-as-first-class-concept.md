# ADR 009 — 청산가와 최대손실은 계획이 아니라 체결 상태에 매단다

- 상태: 채택
- 날짜: 2026-08-21

## 맥락

Phase 1 의 도메인 명세는 `PositionPlan` 에 다음 메서드를 두는 형태였다.

```java
Price liquidationPrice();
Price liquidationPriceIfPartial(int filledCount);
Money maxLossIfAllFilled(Money accountBalance);
```

`liquidationPrice()` 는 인자가 없다. 즉 **계획 하나에 청산가 하나**가 대응한다는 전제가
깔려 있다. 부분 체결은 `~IfPartial` 이라는 별도 메서드로 붙는 예외 취급이다.

이 전제는 이 프로젝트에서 성립하지 않는다. scope.md 의 매매 방식 전제가 **50% 분할 진입**이고,
분할 진입에서는 체결이 진행될 때마다 가중 평단이 움직인다. 청산가 공식이
`liq = entry × (1 ∓ 1/leverage ± MMR)` 이므로 평단이 움직이면 청산가도 움직인다.
최대손실 역시 `수량 × |평단 - 손절가|` 라 마찬가지다.

기준 시나리오(60,000 / 58,000 50% 분할, 손절 56,000, 10배, MMR 0.4%)에서:

| | 1차만 체결 | 전량 체결 |
|---|---|---|
| 평단 | 60,000.00 | 59,000.00 |
| 청산가 | 54,240.00 | 53,336.00 |
| 최대손실 | 10.67 | 16.00 |

`liquidationPrice()` 가 이 중 어느 값을 돌려주어야 하는지 답이 없다.

## 결정

**청산가·수량·최대손실을 `FillState` 라는 값 객체로 묶고, `PositionAnalysis` 가 1건 체결부터
전량 체결까지 순서대로 담는다.** `PositionPlan` 에는 인자 없는 `liquidationPrice()` 를 두지 않는다.

```java
public record FillState(
    int filledEntries, Price averageEntryPrice, Quantity quantity,
    Price liquidationPrice, Money maxLoss) {}

plan.analyze(budget, policy).afterFirstEntry().liquidationPrice();
plan.analyze(budget, policy).whenFullyFilled().maxLoss();
```

`~IfPartial` 계열 메서드도 사라진다. `fillStates` 가 순서대로 담기므로 `filledCount` 를
인자로 받을 이유가 없다.

## 근거

**첫째, 이 프로젝트가 푸는 문제가 정확히 두 값의 차이다.** scope.md 는 "분할 진입 시 총 리스크를
진입 전에 파악할 수 없다" 를 1번 문제로 든다. 1차 체결 시점에 보이는 손실 10.67 과 실제로
확정되어 있는 손실 16.00 의 간극이 그 문제 자체다. 계획에 청산가 하나를 매달면 둘 중 하나가
사라지고, 사라지는 쪽은 대개 부분 체결 값이다 — 도메인이 "정상" 으로 취급하는 쪽이 전량 체결이기
때문이다. **놓치면 안 되는 값이 예외 취급을 받는 구조**가 된다.

**둘째, 안전 검사가 전량 체결 기준으로 좁아진다.** 규칙 "손절가가 청산가 너머면 거부" 를
`liquidationPrice()` 하나로 검사하면 전량 체결만 본다. 그런데 평단이 불리한 쪽은 대개 부분 체결
상태다. 실제로 3배 레버리지의 롱 계획(60,000 / 58,000, 손절 40,000)에서 전량 체결 청산가는
39,569.33 으로 손절가 안쪽이지만, 1차 체결 청산가는 40,240.00 으로 손절가를 넘는다. 상태 목록이
있으면 `states.forEach(this::assert...)` 로 전부 검사하는 것이 자연스럽다.

**셋째, `~IfPartial` 은 개수가 늘어난다.** 값이 하나 늘 때마다 `xIfPartial(int)` 이 따라 붙는다.
Phase 5 의 `TradeRecord` 는 실제 체결(`List<Fill>`)을 다루므로 같은 축이 또 필요해진다.
체결 상태를 일급 개념으로 만들어 두면 그때 재사용할 자리가 생긴다.

## 한계

- `fillStates` 는 **계획된 회차 단위**다. 한 회차가 부분 체결되는 실제 상황은 표현하지 못한다.
  Phase 1 은 진입 전 계산이므로 필요 없지만, Phase 5 에서 실제 체결을 기록할 때는
  `Fill` 이 별도 개념으로 필요하다. `FillState` 를 그 용도로 늘려 쓰지 않는다.
- 회차가 많은 계획에서는 응답이 회차 수만큼 길어진다. 50% 분할(2건)이 전제이므로 현재는
  문제가 아니다. **회차가 5건을 넘는 계획을 다루게 되면 응답 형태를 다시 본다.**

## 철회 조건

**분할 진입을 더 이상 전제하지 않게 되면 이 결정은 근거를 잃는다.** 전량 단일 진입만 다룬다면
체결 상태는 항상 하나뿐이고, `FillState` 는 값 다섯 개를 감싸는 빈 껍데기가 된다.
그때는 `PositionPlan` 에 직접 매다는 편이 낫다.

## 결과

- `PositionPlan` 의 공개 메서드는 `averageEntryPrice`, `averageEntryPriceIfPartial`,
  `totalQuantity`, `requiredMargin`, `riskRewardRatio`, `weakRiskReward`, `analyze` 다.
  평단만 `~IfPartial` 이 남는데, 이것은 `FillState` 를 만드는 재료라 순서상 먼저 있어야 한다.
- API 응답의 `fillStates` 배열이 이 구조를 그대로 드러낸다. 마지막 원소가 전량 체결이다.
- `.claude/skills/domain-model/SKILL.md` 의 포지션 절을 이 결정에 맞춰 갱신했다.

## 관련

- [002 — 부분 헥사고날](002-partial-hexagonal.md) — 구조를 필요한 곳에만 세운다는 같은 태도
- [008 — MaintenanceMarginPolicy 인터페이스](008-maintenance-margin-policy-interface.md) — 청산가 공식의 MMR 입력
- `.claude/docs/scope.md` — "분할 진입 시 총 리스크를 진입 전에 파악할 수 없다"
