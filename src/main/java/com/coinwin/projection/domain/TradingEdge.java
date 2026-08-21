package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/**
 * 매매 우위 — 승률, 손익비, 거래당 리스크 비율. 이 셋이 자산 곡선의 모양을 전부 결정한다.
 *
 * <p>리스크는 <b>현재 자산의 비율</b>이다. 고정 금액이 아니라 비율이므로 자산이 늘면 거는 돈도
 * 늘고, 줄면 거는 돈도 준다. 그래서 자산은 더해지지 않고 곱해진다 — 복리다.
 *
 * <p>승률 50% / 손익비 2 와 승률 25% / 손익비 5 는 {@link #expectancyPerTrade()} 가 같다.
 * 그런데도 지나가는 길은 전혀 다르다. 그 차이를 수치로 내는 것이 이 모듈의 목적이다.
 */
public record TradingEdge(
        Percentage winRate,
        BigDecimal riskRewardRatio,
        Percentage riskPerTrade) {

    /** 승패 추첨의 해상도. {@link Percentage} 스케일 4 가 표현하는 최소 단위(0.0001%)와 맞췄다. */
    private static final int RESOLUTION = 1_000_000;

    private static final Percentage WHOLE = Percentage.of("100");

    public TradingEdge {
        DomainValues.required(winRate, "승률");
        DomainValues.required(riskRewardRatio, "손익비");
        DomainValues.required(riskPerTrade, "거래당 리스크 비율");
        assertAtMostWhole(winRate, "승률");
        assertAtMostWhole(riskPerTrade, "거래당 리스크 비율");
        assertIsPositive(riskPerTrade.value(), "거래당 리스크 비율");
        assertIsPositive(riskRewardRatio, "손익비");
    }

    /**
     * 이 거래가 끝난 뒤 자산에 곱해질 배수. 승리 {@code 1 + r×R}, 패배 {@code 1 - r}.
     *
     * <p>금액이 아니라 배수를 돌려준다. 자산의 어느 시점에 적용되는지는 이 객체의 관심사가
     * 아니고, 배수로 두어야 여러 거래를 한꺼번에 곱해 누적할 수 있다.
     */
    public BigDecimal factorFor(TradeOutcome outcome) {
        BigDecimal risk = riskPerTrade.asFraction();
        return switch (DomainValues.required(outcome, "거래 결과")) {
            case WIN -> BigDecimal.ONE.add(risk.multiply(riskRewardRatio));
            case LOSS -> BigDecimal.ONE.subtract(risk);
        };
    }

    /** R 배수로 표현한 거래당 기댓값. {@code 승률×R - 패률}. 0 이면 본전, 음수면 지는 게임이다. */
    public BigDecimal expectancyPerTrade() {
        BigDecimal winProbability = winRate.asFraction();
        return winProbability.multiply(riskRewardRatio)
                .subtract(BigDecimal.ONE.subtract(winProbability));
    }

    /**
     * 난수 하나를 승패로 바꾼다.
     *
     * <p>실수 난수와 비교하지 않고 백만 분의 1 정수로 비교한다. 부동소수점 비교는 경계에서
     * 구현에 따라 갈릴 수 있고, 그러면 "같은 시드는 같은 결과" 라는 약속이 깨진다.
     */
    public TradeOutcome drawOutcome(RandomGenerator random) {
        DomainValues.required(random, "난수원");
        return random.nextInt(RESOLUTION) < winThreshold() ? TradeOutcome.WIN : TradeOutcome.LOSS;
    }

    private int winThreshold() {
        return winRate.asFraction().multiply(BigDecimal.valueOf(RESOLUTION)).intValue();
    }

    private static void assertAtMostWhole(Percentage percent, String label) {
        if (percent.isGreaterThan(WHOLE)) {
            throw new InvalidValueException(
                    label + "은(는) 100% 를 넘을 수 없다: " + percent.value().toPlainString());
        }
    }

    private static void assertIsPositive(BigDecimal value, String label) {
        if (value.signum() <= 0) {
            throw new InvalidValueException(
                    label + "은(는) 0 보다 커야 한다: " + value.toPlainString());
        }
    }
}
