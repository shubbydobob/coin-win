package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 24시간 요약. 현재가와 그 하루의 폭.
 *
 * <p>변동률은 <b>음수를 허용한다.</b> {@code Percentage} 를 쓰지 않는 이유가 그것이고,
 * {@code FundingRate} 가 같은 이유로 {@code Percentage} 를 안 쓴다.
 */
class TickerTest {

    private static final Instant AT = Instant.parse("2026-08-23T09:00:00Z");

    @Test
    void 하락한_날의_변동률은_음수다() {
        assertThat(시세("76567.50", "-1.009").change24hPercent()).isEqualByComparingTo("-1.009000");
    }

    @Test
    void 상승한_날의_변동률은_양수다() {
        assertThat(시세("76567.50", "2.5").change24hPercent()).isEqualByComparingTo("2.500000");
    }

    @Test
    void 최저가는_최고가보다_높을_수_없다() {
        assertThatThrownBy(() -> new Ticker(
                        Symbol.of("BTCUSDT"),
                        Price.of("76567.50"),
                        new BigDecimal("1.0"),
                        Price.of("75000.00"),
                        Price.of("78000.00"),
                        Quantity.of("100"),
                        AT))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 현재가가_하루_범위_밖이면_받아들이지_않는다() {
        assertThatThrownBy(() -> new Ticker(
                        Symbol.of("BTCUSDT"),
                        Price.of("80000.00"),
                        new BigDecimal("1.0"),
                        Price.of("78000.00"),
                        Price.of("75000.00"),
                        Quantity.of("100"),
                        AT))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 관측_시각은_null_일_수_없다() {
        assertThatThrownBy(() -> new Ticker(
                        Symbol.of("BTCUSDT"),
                        Price.of("76567.50"),
                        new BigDecimal("1.0"),
                        Price.of("78000.00"),
                        Price.of("75000.00"),
                        Quantity.of("100"),
                        null))
                .isInstanceOf(InvalidValueException.class);
    }

    private static Ticker 시세(String last, String change) {
        return new Ticker(
                Symbol.of("BTCUSDT"),
                Price.of(last),
                new BigDecimal(change),
                Price.of("78000.00"),
                Price.of("75000.00"),
                Quantity.of("113033.306"),
                AT);
    }
}
