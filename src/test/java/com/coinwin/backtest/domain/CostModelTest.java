package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import org.junit.jupiter.api.Test;

class CostModelTest {

    private static final CostModel BINANCE = CostModel.binanceDefaults();

    @Test
    void 기본값은_maker_0_02_taker_0_05_슬리피지_0_02_퍼센트다() {
        assertThat(BINANCE.makerFee()).isEqualTo(Percentage.of("0.02"));
        assertThat(BINANCE.takerFee()).isEqualTo(Percentage.of("0.05"));
        assertThat(BINANCE.slippage()).isEqualTo(Percentage.of("0.02"));
    }

    /**
     * 진입은 대 경계에 미리 걸어 두는 지정가라 maker, 청산은 트리거 체결이라 taker 다.
     * 주문 종류와 비용이 일치한다.
     */
    @Test
    void 진입은_maker_청산은_taker_로_계산한다() {
        Money notional = Money.of("10000");

        assertThat(BINANCE.entryFee(notional)).isEqualTo(Money.of("2"));
        assertThat(BINANCE.exitFee(notional)).isEqualTo(Money.of("5"));
    }

    /** 슬리피지는 체결가를 <b>불리한 쪽</b>으로 민다. 롱 청산은 팔아야 하므로 낮게 체결된다. */
    @Test
    void 롱_청산_슬리피지는_가격을_낮추고_숏_청산은_높인다() {
        Price price = Price.of("60000");

        // 60000 × 0.02% = 12
        assertThat(BINANCE.applyExitSlippage(price, Direction.LONG)).isEqualTo(Price.of("59988"));
        assertThat(BINANCE.applyExitSlippage(price, Direction.SHORT)).isEqualTo(Price.of("60012"));
    }

    /**
     * 비용 0 모델. 같은 스펙을 두 번 돌려 나란히 놓으면 <b>수수료가 엣지를 먹어 치우는지</b>가
     * 수치로 나온다. 그것이 이 모델이 존재하는 유일한 이유다.
     */
    @Test
    void 비용_0_모델은_수수료도_슬리피지도_없다() {
        CostModel free = CostModel.free();

        assertThat(free.entryFee(Money.of("10000"))).isEqualTo(Money.of("0"));
        assertThat(free.exitFee(Money.of("10000"))).isEqualTo(Money.of("0"));
        assertThat(free.applyExitSlippage(Price.of("60000"), Direction.LONG))
                .isEqualTo(Price.of("60000"));
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new CostModel(null, Percentage.of("0"), Percentage.of("0")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new CostModel(Percentage.of("0"), null, Percentage.of("0")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new CostModel(Percentage.of("0"), Percentage.of("0"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
