package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 봇이 읽어낸 것을 담는 자리.
 *
 * <p><b>이 스위트가 지키는 것은 하나다 — 못 읽었다는 사실이 값으로 남는다.</b> 빈 값을
 * 기본값으로 채우면 전략이 침묵과 관측을 구별할 수 없고, 그 위에서 주문이 나간다.
 */
class MarketReadingTest {

    private static final Instant AT = Instant.parse("2026-09-19T00:00:00Z");

    /** <b>"아무 일도 없다" 가 아니라 "아무것도 못 읽었다" 다.</b> 이름이 그 구분을 나른다. */
    @Test
    void 아무것도_못_읽은_판독은_셋_다_비어_있다() {
        MarketReading none = MarketReading.none();

        assertThat(none.readout()).isEmpty();
        assertThat(none.book()).isEmpty();
        assertThat(none.outliers()).isEmpty();
        assertThat(none.complete()).isFalse();
    }

    /** 하나라도 비면 완전하지 않다. 셋을 전부 요구하는 전략이 한 번에 묻는 자리다. */
    @Test
    void 하나라도_비면_완전하지_않다() {
        MarketReading onlyBook = new MarketReading(
                Optional.empty(), Optional.of(book()), Optional.empty());

        assertThat(onlyBook.book()).isPresent();
        assertThat(onlyBook.complete()).isFalse();
    }

    /**
     * {@code null} 과 빈 {@link Optional} 은 다른 것이다. 앞은 실수이고 뒤는 사실이라,
     * 앞을 허용하면 "못 읽었다" 를 나르려던 자리가 그냥 터진다.
     */
    @Test
    void 칸_자체가_null_일_수는_없다() {
        assertThatThrownBy(() -> new MarketReading(null, Optional.empty(), Optional.empty()))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketReading(Optional.empty(), null, Optional.empty()))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketReading(Optional.empty(), Optional.empty(), null))
                .isInstanceOf(InvalidValueException.class);
    }

    private static OrderBook book() {
        return new OrderBook(Symbol.BTC_USDT,
                List.of(new PriceLevel(Price.of("77990"), Quantity.of("1"))),
                List.of(new PriceLevel(Price.of("78010"), Quantity.of("1"))), AT);
    }
}
