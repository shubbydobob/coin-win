package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 목표를 지키려면 거래 한 건이 무엇을 해야 하는가. 네 수가 같은 거래 하나를 네 각도에서 본다.
 *
 * <p>{@code 순수익 + 비용 = 총수익} 이고 {@code 총수익 = 레버리지 × 가격 변동} 이다. 두 식이
 * 이 record 의 전부이며, 네 값을 함께 내는 이유는 <b>화면이 그 산수를 다시 하지 않게</b>
 * 하기 위해서다.
 */
public record RequiredEdge(
        Percentage netPerTrade,
        Percentage costPerTrade,
        Percentage grossPerTrade,
        Percentage priceMove) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public RequiredEdge {
        DomainValues.required(netPerTrade, "거래당 필요 순수익");
        DomainValues.required(costPerTrade, "거래당 비용");
        DomainValues.required(priceMove, "거래당 필요 가격 변동");
        if (DomainValues.required(grossPerTrade, "거래당 필요 총수익").value().signum() == 0) {
            throw new InvalidProjectionException(
                    "거래당 필요 총수익이 반올림해서 0 이 됐다 — 목표가 너무 작거나 월 거래 수가 너무 많다");
        }
    }

    static RequiredEdge of(Percentage monthlyTarget, TradingCost cost) {
        return new RequiredEdge(
                asPercent(cost.netPerTrade(monthlyTarget)),
                asPercent(cost.perTrade()),
                asPercent(cost.grossPerTrade(monthlyTarget)),
                asPercent(cost.priceMovePerTrade(monthlyTarget)));
    }

    /**
     * 필요한 총수익 중 비용이 가져가는 몫.
     *
     * <p><b>이 수 하나가 레버리지의 맞바꿈을 드러낸다.</b> 레버리지를 올리면 필요한 가격
     * 변동은 작아지는데 비용의 몫은 그만큼 커진다 — 두 수를 따로 보면 그 맞바꿈이 보이지
     * 않고, 레버리지가 공짜로 목표를 쉽게 만들어 주는 것처럼 읽힌다.
     */
    public Percentage costShare() {
        return Percentage.of(costPerTrade.value()
                .multiply(HUNDRED)
                .divide(grossPerTrade.value(), MathContext.DECIMAL128));
    }

    /** 소수 비율(0.014)을 백분율 값 객체(1.4%)로 옮긴다. 반올림 정책은 값 객체가 갖는다. */
    private static Percentage asPercent(BigDecimal fraction) {
        return Percentage.of(fraction.multiply(HUNDRED));
    }
}
