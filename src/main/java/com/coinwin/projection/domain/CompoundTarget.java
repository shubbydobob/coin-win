package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 목표 복리. <b>월 목표 수익률을 그대로 지켰을 때</b> 자산이 어디로 가고, 그 목표가 거래
 * 한 건에 무엇을 요구하는가.
 *
 * <p>{@link ProjectionSpec} 과 묻는 것이 다르다. 그쪽은 승률과 손익비를 주면 <b>결과가 어떻게
 * 갈리는지</b>를 내고, 이쪽은 결과를 정해 놓고 <b>그러려면 무엇이 필요한지</b>를 낸다.
 *
 * <p><b>이 계산에는 지는 거래가 없다.</b> 모든 거래가 필요한 만큼 정확히 번다고 가정한다.
 * 그래서 나온 수는 "얼마 벌 것이다" 가 아니라 "이만큼을 원하면 이만큼이 필요하다" 로만
 * 읽어야 한다 — 지는 거래가 섞이면 이기는 거래는 여기 적힌 것보다 더 크게 벌어야 한다.
 * 그 갈림을 재는 것은 {@link MonteCarloProjection} 쪽이다.
 *
 * <p>비용은 목표 자체를 깎지 않는다. 목표는 <b>비용을 낸 뒤에 남는 수익</b>이고, 비용이
 * 하는 일은 그 목표에 필요한 가격 변동을 키우는 것이다.
 */
public record CompoundTarget(
        Money startingCapital, Percentage monthlyTarget, int months, TradingCost cost) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public CompoundTarget {
        DomainValues.required(startingCapital, "시작 자산");
        DomainValues.required(monthlyTarget, "월 목표 수익률");
        DomainValues.required(cost, "거래 비용");
        DomainValues.atLeast(months, 1, "기간(개월)");
        assertIsPositive(startingCapital.value(), "시작 자산");
        assertIsPositive(monthlyTarget.value(), "월 목표 수익률");
        assertWithinTradeCap(months, cost);
    }

    /** 거래 한 건에 요구되는 것 넷. */
    public RequiredEdge requiredEdge() {
        return RequiredEdge.of(monthlyTarget, cost);
    }

    public int totalTrades() {
        return cost.tradesOver(months);
    }

    /** 시작 시점에 시장에 나가는 크기. 자산이 아니라 이쪽에 수수료가 붙는다. */
    public Money notional() {
        return cost.notionalOf(startingCapital);
    }

    /**
     * 월말마다 한 점. 첫 점은 거래 이전의 시작 자산이므로 점은 개월 수보다 하나 많다.
     *
     * <p>각 점은 직전 점이 아니라 <b>시작 자산에</b> 누적 배수를 곱해 얻는다.
     * {@link ProjectionSpec#project} 와 같은 이유다 — 점마다 센트로 반올림한 값을 다시 곱하면
     * 오차가 개월 수만큼 쌓인다.
     */
    public List<Money> monthlyEquity() {
        List<Money> points = new ArrayList<>(months + 1);
        for (int month = 0; month <= months; month++) {
            points.add(startingCapital.times(growthOver(month)));
        }
        return List.copyOf(points);
    }

    public Money finalEquity() {
        return startingCapital.times(growthOver(months));
    }

    /**
     * 기간 동안 늘어난 금액. 사람이 "얼마 버나" 로 묻는 수다.
     *
     * <p>도착점과 함께 내는 이유는 화면이 뺄셈을 하지 않게 하기 위해서다 — 화면에 나오는
     * 모든 수는 응답에 그대로 있던 수여야 한다(`docs/adr/020`).
     */
    public Money totalProfit() {
        return finalEquity().minus(startingCapital);
    }

    /** 기간 전체 수익률. 월 5% 를 열두 달 지키면 60% 가 아니라 79.5856% 다. */
    public Percentage totalReturn() {
        return Percentage.of(growthOver(months).subtract(BigDecimal.ONE).multiply(HUNDRED));
    }

    /**
     * 기간 동안 수수료와 슬리피지로 나가는 총액.
     *
     * <p>자산이 불어나면 같은 비율의 비용도 함께 불어나므로 거래마다 다시 센다. 거래 수만큼
     * 도는 이 반복이 곱셈 한 번보다 정직하다 — 닫힌 식은 순수익률이 0 일 때 0 으로 나눈다.
     *
     * <p><b>이 수가 시작 자산을 넘는 것은 흔한 일이다.</b> 레버리지 10배로 월 20건이면
     * 왕복 비용만 자산의 1.4% 이고, 그것을 240번 낸다.
     */
    public Money totalCost() {
        BigDecimal factor = BigDecimal.ONE.add(cost.netPerTrade(monthlyTarget));
        BigDecimal grown = BigDecimal.ONE;
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int trade = 0; trade < totalTrades(); trade++) {
            accumulated = accumulated.add(grown);
            grown = grown.multiply(factor, MathContext.DECIMAL128);
        }
        return startingCapital.times(
                accumulated.multiply(cost.perTrade(), MathContext.DECIMAL128));
    }

    /**
     * 비용을 내기 <b>전에</b> 번 돈. {@code 순이익 + 총 비용}.
     *
     * <p><b>이 수가 없으면 화면의 뺄셈이 닫히지 않는다.</b> 순이익 옆에 비용만 놓으면
     * "6,332 를 버는데 9,681 을 낸다" 로 읽히고, 그것은 손실처럼 보인다. 실제로는 16,014 를
     * 벌어 9,681 을 내고 6,332 가 남는 것이다.
     *
     * <p>화면이 두 수를 더해 만들지 않는 이유는 {@link #totalProfit} 과 같다 —
     * 화면에 나오는 모든 수는 응답에 그대로 있던 수여야 한다({@code docs/adr/020}).
     */
    public Money grossProfit() {
        return totalProfit().plus(totalCost());
    }

    private BigDecimal growthOver(int elapsed) {
        return BigDecimal.ONE.add(monthlyTarget.asFraction())
                .pow(elapsed, MathContext.DECIMAL128);
    }

    private static void assertIsPositive(BigDecimal value, String label) {
        if (value.signum() <= 0) {
            throw new InvalidValueException(
                    label + "은(는) 0 보다 커야 한다: " + value.toPlainString());
        }
    }

    /** 상한의 뜻은 {@link TradeFrequency} 와 같다 — 요청 하나가 서버를 잡지 못하게 한다. */
    private static void assertWithinTradeCap(int months, TradingCost cost) {
        long total = Math.multiplyFull(cost.tradesPerMonth(), months);
        if (total > TradeFrequency.MAXIMUM_TRADES) {
            throw new InvalidProjectionException(
                    "총 거래 수는 " + TradeFrequency.MAXIMUM_TRADES + " 을 넘을 수 없다: "
                            + cost.tradesPerMonth() + " × " + months);
        }
    }
}
