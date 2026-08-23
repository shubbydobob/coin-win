package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import org.junit.jupiter.api.Test;

/** 호가 한 단. */
class PriceLevelTest {

    @Test
    void 가격과_잔량을_그대로_갖는다() {
        PriceLevel level = new PriceLevel(Price.of("76567.40"), Quantity.of("24.778"));

        assertThat(level.price().value()).isEqualByComparingTo("76567.40");
        assertThat(level.quantity().value()).isEqualByComparingTo("24.77800000");
    }

    /**
     * <b>잔량 0 은 호가가 아니다.</b> 거래소는 잔량이 0 이 된 단을 목록에서 빼고 주므로, 0 이
     * 오는 것은 응답을 잘못 읽었다는 뜻이다. 담아 두면 잔량 합과 불균형이 조용히 틀린다.
     */
    @Test
    void 잔량이_0_이면_호가가_아니다() {
        assertThatThrownBy(() -> new PriceLevel(Price.of("100.00"), Quantity.of("0")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 가격과_잔량은_null_일_수_없다() {
        assertThatThrownBy(() -> new PriceLevel(null, Quantity.of("1")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new PriceLevel(Price.of("100.00"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
