package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RequiredEdgeTest {

    private static RequiredEdge 요구(String 레버리지) {
        return RequiredEdge.of(
                Percentage.of("5"),
                new TradingCost(
                        Percentage.of("0.05"), Percentage.of("0.02"),
                        new BigDecimal(레버리지), Percentage.of("100"), 20));
    }

    @Test
    void 순수익과_비용을_더하면_총수익이다() {
        RequiredEdge 요구 = 요구("10");

        assertThat(요구.netPerTrade().value().add(요구.costPerTrade().value()))
                .isEqualByComparingTo(요구.grossPerTrade().value());
    }

    /**
     * 등식은 <b>값 객체의 스케일 안에서</b> 성립한다. 네 값이 각각 0.0001% 로 반올림되므로
     * 레버리지를 곱하면 그만큼의 어긋남이 남는다 — 어긋남이 없다고 적으면 그것이 거짓말이다.
     * 반올림 이전의 정확한 등식은 {@code TradingCostTest} 가 소수 그대로 확인한다.
     */
    @Test
    void 총수익은_레버리지에_가격_변동을_곱한_것이다() {
        RequiredEdge 요구 = 요구("10");

        assertThat(요구.priceMove().value().multiply(new BigDecimal("10")).doubleValue())
                .isCloseTo(요구.grossPerTrade().value().doubleValue(), within(0.001));
    }

    /**
     * 이 모듈이 답해야 하는 질문. 레버리지는 필요한 가격 변동을 줄여 주지만 <b>공짜가 아니다</b> —
     * 같은 만큼 비용의 몫이 커진다. 두 수를 따로 보면 그 맞바꿈이 보이지 않는다.
     */
    @Test
    void 레버리지를_올리면_필요_가격_변동은_작아지지만_비용의_몫은_커진다() {
        RequiredEdge 저배율 = 요구("2");
        RequiredEdge 고배율 = 요구("20");

        assertThat(고배율.priceMove().value()).isLessThan(저배율.priceMove().value());
        assertThat(고배율.costShare().isGreaterThan(저배율.costShare())).isTrue();
    }

    @Test
    void 비용이_0_이면_비용의_몫도_0_이다() {
        RequiredEdge 요구 = RequiredEdge.of(
                Percentage.of("5"),
                new TradingCost(
                        Percentage.of("0"), Percentage.of("0"), BigDecimal.TEN,
                        Percentage.of("100"), 20));

        assertThat(요구.costShare().value()).isEqualByComparingTo("0");
    }

    /** 반올림해서 0 이 된 총수익으로 비율을 내면 0 으로 나눈다. 그 전에 거절한다. */
    @Test
    void 거래당_필요_총수익이_0_이면_성립하지_않는다() {
        assertThatThrownBy(() -> new RequiredEdge(
                Percentage.of("0"), Percentage.of("0"), Percentage.of("0"), Percentage.of("0")))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("반올림해서 0");
    }

    @Test
    void 필요_순수익이_없으면_거부한다() {
        assertThatThrownBy(() -> new RequiredEdge(
                null, Percentage.of("1"), Percentage.of("1"), Percentage.of("1")))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("거래당 필요 순수익");
    }
}
