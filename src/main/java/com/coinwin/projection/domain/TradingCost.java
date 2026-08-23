package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 거래 한 건에 붙는 비용과 그 비용이 실리는 크기 — 수수료율, 슬리피지, 레버리지,
 * 거래당 투입 비율, 월 거래 수.
 *
 * <p><b>수수료와 슬리피지는 증거금이 아니라 명목에 붙는다.</b> 그래서 레버리지가 비용을 그대로
 * 곱한다 — 10배로 들어가면 왕복 수수료 0.1% 가 자산의 1% 다. 이 한 문장이 이 record 가
 * 존재하는 이유의 전부이고, 레버리지를 "필요한 가격 변동을 줄여 주는 것" 으로만 읽을 때
 * 보이지 않는 쪽이다.
 *
 * <p><b>명목을 정하는 것은 레버리지 혼자가 아니다.</b> 초안은 {@code 명목 = 자산 × 레버리지}
 * 였는데, 그것은 <b>매 거래에 자산 전액을 증거금으로 넣는다</b>는 뜻이다. 실제로는 거래당
 * 일부만 넣는다. 그 전제를 그대로 두면 비용이 실제보다 몇 배 크게 나오고, 이 화면이 하려는
 * 경고가 "말이 안 되는 수" 가 되어 아무도 안 믿게 된다.
 *
 * <p>그래서 계산에 실제로 쓰이는 것은 {@link #effectiveLeverage()} 하나다 —
 * {@code 투입 비율 × 레버리지}. 자산의 20% 를 10배로 넣는 것과 전액을 2배로 넣는 것은
 * 비용도 필요 가격 변동도 같다. <b>둘을 따로 받는 이유는 사람이 그 둘을 따로 정하기
 * 때문</b>이지 계산이 그것을 구별해서가 아니다.
 *
 * <p>비용은 왕복으로 센다. 들어갈 때 한 번, 나올 때 한 번 낸다.
 *
 * <p>수수료율의 기본값을 여기 두지 않는다. 거래소의 수수료는 VIP 등급과 프로모션에 따라
 * 달라지고, 도메인이 그 값을 들고 있으면 <b>거래소가 바꾼 날 이 코드만 옛 숫자를 말한다.</b>
 * 부르는 쪽이 매번 실어 보낸다.
 */
public record TradingCost(
        Percentage feeRate,
        Percentage slippage,
        BigDecimal leverage,
        Percentage marginUsage,
        int tradesPerMonth) {

    /** 진입과 청산. 비용은 거래 한 건마다 두 번 난다. */
    private static final BigDecimal ROUND_TRIP = BigDecimal.TWO;

    /** 실효 배율의 스케일. 무차원 배수라 손익비와 같은 자릿수를 쓴다. */
    private static final int LEVERAGE_SCALE = 2;

    private static final Percentage WHOLE = Percentage.of("100");

    public TradingCost {
        DomainValues.required(feeRate, "수수료율");
        DomainValues.required(slippage, "슬리피지");
        DomainValues.required(leverage, "레버리지");
        DomainValues.required(marginUsage, "거래당 투입 비율");
        DomainValues.atLeast(tradesPerMonth, 1, "월 거래 수");
        if (leverage.signum() <= 0) {
            throw new InvalidValueException(
                    "레버리지는 0 보다 커야 한다: " + leverage.toPlainString());
        }
        if (marginUsage.value().signum() <= 0 || marginUsage.isGreaterThan(WHOLE)) {
            throw new InvalidValueException(
                    "거래당 투입 비율은 0 초과 100 이하여야 한다: "
                            + marginUsage.value().toPlainString());
        }
    }

    /**
     * 명목이 자산의 몇 배인가. {@code 투입 비율 × 레버리지}.
     *
     * <p>비용도 필요 가격 변동도 전부 이 하나로 결정된다. 자산의 20% 를 10배로 넣는 것과
     * 전액을 2배로 넣는 것은 이 값이 같고, 따라서 결과도 같다.
     */
    public BigDecimal effectiveLeverage() {
        return DomainValues.scaled(
                leverage.multiply(marginUsage.asFraction()), LEVERAGE_SCALE, "실효 배율");
    }

    /**
     * 거래 한 건이 <b>자산에서</b> 떼어 가는 비율.
     * {@code 실효 배율 × (수수료 + 슬리피지) × 2}.
     *
     * <p>명목 기준이 아니라 자산 기준으로 내는 것이 요점이다. 목표 수익률도 자산 기준이므로
     * 같은 눈금이어야 두 수를 더하고 뺄 수 있다.
     */
    public BigDecimal perTrade() {
        return feeRate.asFraction()
                .add(slippage.asFraction())
                .multiply(ROUND_TRIP)
                .multiply(effectiveLeverage());
    }

    /**
     * 월 목표를 거래당 순수익률로 쪼갠다. {@code (1 + 월목표)^(1/월 거래 수) − 1}.
     *
     * <p><b>나누지 않고 제곱근을 쓴다.</b> 거래마다 자산이 불어나므로 월 5% 를 20 건으로 나눈
     * 0.25% 씩 벌면 한 달 뒤에는 5% 를 넘는다. 나눗셈은 필요한 것보다 큰 수를 요구하고,
     * 그러면 목표가 실제보다 어려워 보인다.
     *
     * <p>{@link BigDecimal} 에는 n 제곱근이 없다. {@link StrictMath} 를 쓰는 이유는 정밀도가
     * 아니라 <b>재현성</b>이다 — 기계가 달라도 같은 입력이 같은 답을 낸다.
     */
    public BigDecimal netPerTrade(Percentage monthlyTarget) {
        double grown = BigDecimal.ONE
                .add(DomainValues.required(monthlyTarget, "월 목표 수익률").asFraction())
                .doubleValue();
        return BigDecimal.valueOf(StrictMath.pow(grown, 1.0 / tradesPerMonth))
                .subtract(BigDecimal.ONE);
    }

    /** 비용까지 덮으려면 거래당 자산이 얼마나 늘어야 하는가. {@code 순수익 + 비용}. */
    public BigDecimal grossPerTrade(Percentage monthlyTarget) {
        return netPerTrade(monthlyTarget).add(perTrade());
    }

    /**
     * 그러려면 <b>가격</b>이 거래당 얼마나 움직여야 하는가.
     *
     * <p>자산 기준 수익을 실효 배율로 되돌린 값이다. 사람이 차트에서 재는 것은 이 수이지
     * 자산 기준 수익률이 아니다.
     */
    public BigDecimal priceMovePerTrade(Percentage monthlyTarget) {
        return grossPerTrade(monthlyTarget).divide(effectiveLeverage(), MathContext.DECIMAL128);
    }

    /** 자산이 아니라 실제로 시장에 나가는 크기. 수수료가 붙는 것은 이쪽이다. */
    public Money notionalOf(Money equity) {
        return DomainValues.required(equity, "자산").times(effectiveLeverage());
    }

    /** 기간 동안의 총 거래 수. 곱이 {@code int} 를 넘는지는 부르는 쪽이 먼저 본다. */
    public int tradesOver(int months) {
        return tradesPerMonth * months;
    }
}
