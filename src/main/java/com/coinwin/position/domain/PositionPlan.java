package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 진입 전의 매매 계획. 이 모듈이 답하는 질문은 하나다 — <b>전량 체결되면 얼마를 잃는가.</b>
 *
 * <p>분할 진입은 "1차만 들어갔으니 아직 여유 있다" 는 착각을 만든다. 그래서 계획 시점에
 * 부분 체결 상태와 전량 체결 상태를 함께 산출한다. 두 값의 차이가 이 도구의 존재 이유다.
 *
 * <p>사이징 순서는 거꾸로다. 수량을 정하고 손절가를 붙이는 것이 아니라,
 * <b>손절가가 수량을 결정한다.</b>
 */
public record PositionPlan(
        Direction direction,
        EntryLadder entries,
        Price stopLoss,
        Price takeProfit,
        int leverage) {

    private static final int RATIO_SCALE = 2;

    /** 손익비 경고 기준. 미달이어도 거부하지 않는다 — 판단은 사람이 한다. */
    private static final BigDecimal MINIMUM_RISK_REWARD = new BigDecimal("1.5");

    public PositionPlan {
        DomainValues.required(direction, "방향");
        DomainValues.required(entries, "분할 진입 계획");
        DomainValues.required(stopLoss, "손절가");
        DomainValues.required(takeProfit, "익절가");
        if (leverage < 1) {
            throw new InvalidValueException("레버리지는 1 이상이어야 한다: " + leverage);
        }
        assertExitsAreOnCorrectSides(direction, entries, stopLoss, takeProfit);
    }

    public Price averageEntryPrice() {
        return entries.averagePriceAfter(entries.size());
    }

    public Price averageEntryPriceIfPartial(int filledCount) {
        return entries.averagePriceAfter(filledCount);
    }

    /**
     * 전량 체결을 전제로 한 총수량.
     *
     * <p>{@code quantity = riskAmount / |평단 - 손절가|}. 부분 체결 수량은 이 총량을
     * 비중으로 나눈 것이지, 부분 체결 시점에 다시 계산한 값이 아니다.
     */
    public Quantity totalQuantity(RiskBudget budget) {
        DomainValues.required(budget, "리스크 예산");
        return budget.riskAmount().dividedBy(averageEntryPrice().absoluteDifference(stopLoss));
    }

    /** {@code margin = 명목가 / leverage}. 이 돈이 없으면 계획을 열 수 없다. */
    public Money requiredMargin(RiskBudget budget) {
        return totalQuantity(budget).times(averageEntryPrice().asAmount(), leverage);
    }

    /**
     * 표시용 손익비. {@code |익절가 - 평단| / |평단 - 손절가|} 를 <b>버림</b>으로 낸다.
     *
     * <p>반올림이 아니라 버림인 이유는 {@link #weakRiskReward()} 와 어긋나지 않기 위해서다.
     * 실제 손익비 1.495 를 반올림하면 1.50 이 되어, 경고는 켜져 있는데 화면에는 기준을
     * 넘은 것처럼 보인다. 버림이면 "표시값이 1.50 이상" 과 "기준 충족" 이 정확히 일치한다.
     */
    public BigDecimal riskRewardRatio() {
        return exactRiskRewardRatio().setScale(RATIO_SCALE, RoundingMode.DOWN);
    }

    /** 손익비가 기준 미달인가. 판단은 <b>반올림하지 않은</b> 손익비로 한다. */
    public boolean weakRiskReward() {
        return riskRewardBelow(MINIMUM_RISK_REWARD);
    }

    /**
     * 손익비가 주어진 문턱값 미만인가.
     *
     * <p>Phase 6 백테스트가 문턱값을 파라미터로 받기 때문에 열어 둔다. 계획 검토 화면에서는
     * 1.5 가 고정 기준이지만, 백테스트는 <b>그 기준이 실제로 성적을 개선하는지</b>를 재는 것이
     * 목적이라 값을 바꿔 가며 돌려야 한다.
     *
     * <p>비교를 호출부에서 하지 않고 여기 두는 이유는 <b>반올림하지 않은 손익비를 써야 한다</b>는
     * 규칙이 하나여야 하기 때문이다. {@link #riskRewardRatio()} 는 표시용 버림값이므로 그것으로
     * 비교하면 경계에서 판정이 갈린다.
     */
    public boolean riskRewardBelow(BigDecimal threshold) {
        DomainValues.required(threshold, "손익비 문턱값");
        return exactRiskRewardRatio().compareTo(threshold) < 0;
    }

    private BigDecimal exactRiskRewardRatio() {
        Price average = averageEntryPrice();
        return average.absoluteDifference(takeProfit).value()
                .divide(average.absoluteDifference(stopLoss).value(), MathContext.DECIMAL64);
    }

    /**
     * 체결 상태별 산출 결과 전체.
     *
     * <p>손절가가 청산가 너머인지는 <b>모든</b> 체결 상태에서 검사한다. 전량 체결 기준으로만
     * 보면, 1차 체결 상태에서만 청산이 손절보다 앞서는 계획을 놓친다. 평단이 불리한 쪽은
     * 대개 부분 체결 상태다.
     */
    public PositionAnalysis analyze(RiskBudget budget, MaintenanceMarginPolicy policy) {
        DomainValues.required(policy, "유지증거금 정책");
        Quantity total = totalQuantity(budget);
        List<FillState> states = IntStream.rangeClosed(1, entries.size())
                .mapToObj(filled -> FillState.of(this, filled, total, policy))
                .toList();
        states.forEach(this::assertStopIsReachedBeforeLiquidation);
        return new PositionAnalysis(states, requiredMargin(budget), budget.accountBalance(),
                riskRewardRatio(), weakRiskReward());
    }

    private void assertStopIsReachedBeforeLiquidation(FillState state) {
        boolean safe = switch (direction) {
            case LONG -> stopLoss.isAbove(state.liquidationPrice());
            case SHORT -> stopLoss.isBelow(state.liquidationPrice());
        };
        if (!safe) {
            throw new StopBeyondLiquidationException(
                    state.filledEntries(), stopLoss, state.liquidationPrice());
        }
    }

    private static void assertExitsAreOnCorrectSides(
            Direction direction, EntryLadder entries, Price stopLoss, Price takeProfit) {
        Price lowest = entries.lowestPrice();
        Price highest = entries.highestPrice();
        Optional<String> violation = switch (direction) {
            case LONG -> longViolation(stopLoss, takeProfit, lowest, highest);
            case SHORT -> shortViolation(stopLoss, takeProfit, lowest, highest);
        };
        violation.ifPresent(rule -> {
            throw new InvalidPositionPlanException(rule);
        });
    }

    private static Optional<String> longViolation(
            Price stopLoss, Price takeProfit, Price lowest, Price highest) {
        if (!stopLoss.isBelow(lowest)) {
            return Optional.of("롱 계획의 손절가는 최저 진입가보다 낮아야 한다");
        }
        if (!takeProfit.isAbove(highest)) {
            return Optional.of("롱 계획의 익절가는 최고 진입가보다 높아야 한다");
        }
        return Optional.empty();
    }

    private static Optional<String> shortViolation(
            Price stopLoss, Price takeProfit, Price lowest, Price highest) {
        if (!stopLoss.isAbove(highest)) {
            return Optional.of("숏 계획의 손절가는 최고 진입가보다 높아야 한다");
        }
        if (!takeProfit.isBelow(lowest)) {
            return Optional.of("숏 계획의 익절가는 최저 진입가보다 낮아야 한다");
        }
        return Optional.empty();
    }
}
