package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class TradingCostTest {

    /** 바이낸스 USDⓈ-M 무기한의 일반 사용자 테이커 수수료. 숫자의 출처는 화면 기본값이 아니라 요청이다. */
    private static final String TAKER = "0.05";

    /** 전액 투입. 명목이 자산 × 레버리지가 되어 실효 배율이 곧 레버리지다. */
    private static final Percentage FULL = Percentage.of("100");

    private static TradingCost 비용(String 수수료, String 슬리피지, String 레버리지, int 월거래) {
        return new TradingCost(
                Percentage.of(수수료), Percentage.of(슬리피지), new BigDecimal(레버리지),
                FULL, 월거래);
    }

    /** 투입 비율만 달리 본다. 수수료와 슬리피지는 위와 같다. */
    private static TradingCost 부분투입(String 레버리지, String 투입비율, int 월거래) {
        return new TradingCost(
                Percentage.of(TAKER), Percentage.of("0.02"), new BigDecimal(레버리지),
                Percentage.of(투입비율), 월거래);
    }

    @Test
    void 거래당_비용은_수수료와_슬리피지_합의_왕복에_레버리지를_곱한_것이다() {
        // (0.05% + 0.02%) × 2 × 10배 = 자산의 1.4%
        assertThat(비용(TAKER, "0.02", "10", 20).perTrade()).isEqualByComparingTo("0.014");
    }

    @Test
    void 레버리지가_두_배면_비용도_두_배다() {
        BigDecimal 열배 = 비용(TAKER, "0.02", "10", 20).perTrade();
        BigDecimal 스무배 = 비용(TAKER, "0.02", "20", 20).perTrade();

        assertThat(스무배).isEqualByComparingTo(열배.multiply(BigDecimal.TWO));
    }

    /** 비용을 0 으로 두면 이 모듈은 순수한 복리 계산기가 된다. 그 경계가 성립해야 한다. */
    @Test
    void 수수료와_슬리피지가_0_이면_거래당_비용도_0_이다() {
        assertThat(비용("0", "0", "10", 20).perTrade()).isEqualByComparingTo("0");
    }

    /**
     * 이 record 의 존재 이유. 거래당 순수익률을 한 달 치 곱하면 월 목표가 정확히 나와야 하고,
     * 그렇지 않으면 화면의 두 수(월별 자산 곡선과 거래당 필요 수익)가 다른 이야기를 한다.
     */
    @Test
    void 거래당_순수익률을_한_달_치_반복하면_월_목표가_된다() {
        BigDecimal 거래당 = 비용(TAKER, "0.02", "10", 20).netPerTrade(Percentage.of("5"));

        BigDecimal 한달 = BigDecimal.ONE.add(거래당).pow(20, MathContext.DECIMAL128);

        assertThat(한달.doubleValue()).isCloseTo(1.05, within(1e-12));
    }

    @Test
    void 나누지_않고_제곱근을_쓴다_복리이므로_단순히_나눈_값보다_작다() {
        BigDecimal 거래당 = 비용(TAKER, "0.02", "10", 20).netPerTrade(Percentage.of("5"));

        // 5% ÷ 20 = 0.25%. 제곱근은 그보다 작다 — 거래마다 자산이 불어나기 때문이다.
        assertThat(거래당).isLessThan(new BigDecimal("0.0025"));
    }

    @Test
    void 필요_총수익은_순수익에_비용을_더한_것이다() {
        TradingCost 비용 = 비용(TAKER, "0.02", "10", 20);
        Percentage 목표 = Percentage.of("5");

        assertThat(비용.grossPerTrade(목표))
                .isEqualByComparingTo(비용.netPerTrade(목표).add(비용.perTrade()));
    }

    @Test
    void 필요_가격_변동은_총수익을_레버리지로_되돌린_값이다() {
        TradingCost 비용 = 비용(TAKER, "0.02", "10", 20);
        Percentage 목표 = Percentage.of("5");

        assertThat(비용.priceMovePerTrade(목표).multiply(new BigDecimal("10")).doubleValue())
                .isCloseTo(비용.grossPerTrade(목표).doubleValue(), within(1e-15));
    }

    @Test
    void 명목은_자산에_레버리지를_곱한_것이다() {
        assertThat(비용(TAKER, "0.02", "10", 20).notionalOf(Money.of("800")).value())
                .isEqualByComparingTo("8000.00");
    }

    @Test
    void 기간_동안의_총_거래_수는_월_거래_수에_개월을_곱한_것이다() {
        assertThat(비용(TAKER, "0.02", "10", 20).tradesOver(12)).isEqualTo(240);
    }

    /**
     * <b>비용이 "말이 안 되게" 커 보였던 자리.</b> 명목 = 자산 × 레버리지 는 매 거래에 전액을
     * 증거금으로 넣는다는 뜻이다. 실제로는 일부만 넣고, 그 비율이 비용을 그대로 나눈다.
     */
    @Test
    void 투입_비율이_비용을_그대로_줄인다() {
        assertThat(부분투입("10", "20", 20).perTrade()).isEqualByComparingTo("0.0028");
    }

    /** 계산이 보는 것은 둘의 곱 하나뿐이다. 사람이 둘을 따로 정하기 때문에 따로 받는다. */
    @Test
    void 자산의_20퍼센트를_10배는_전액을_2배와_같다() {
        TradingCost 일부 = 부분투입("10", "20", 20);
        TradingCost 전액 = 부분투입("2", "100", 20);
        Percentage 목표 = Percentage.of("5");

        assertThat(일부.effectiveLeverage()).isEqualByComparingTo(전액.effectiveLeverage());
        assertThat(일부.perTrade()).isEqualByComparingTo(전액.perTrade());
        assertThat(일부.priceMovePerTrade(목표)).isEqualByComparingTo(전액.priceMovePerTrade(목표));
    }

    @Test
    void 실효_배율은_투입_비율에_레버리지를_곱한_것이다() {
        assertThat(부분투입("10", "20", 20).effectiveLeverage()).isEqualByComparingTo("2.00");
    }

    @Test
    void 투입_비율이_0_이거나_100_을_넘으면_거부한다() {
        assertThatThrownBy(() -> 부분투입("10", "0", 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("거래당 투입 비율");
        assertThatThrownBy(() -> 부분투입("10", "101", 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("거래당 투입 비율");
    }

    @Test
    void 레버리지가_0_이하면_거부한다() {
        assertThatThrownBy(() -> 비용(TAKER, "0.02", "0", 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("레버리지");
    }

    @Test
    void 월_거래_수가_0_이면_거부한다() {
        assertThatThrownBy(() -> 비용(TAKER, "0.02", "10", 0))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("월 거래 수");
    }

    @Test
    void 수수료율이_없으면_거부한다() {
        assertThatThrownBy(() -> new TradingCost(
                null, Percentage.of("0.02"), BigDecimal.TEN, FULL, 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("수수료율");
    }

    @Test
    void 월_목표가_없으면_거부한다() {
        assertThatThrownBy(() -> 비용(TAKER, "0.02", "10", 20).netPerTrade(null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("월 목표 수익률");
    }
}
