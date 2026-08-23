package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 한 순간의 호가.
 *
 * <p><b>이 타입은 방향을 말하지 않는다.</b> 불균형이 양수라고 오른다는 뜻이 아니다 — 호가는
 * 취소될 수 있고 큰 벽은 오히려 미끼인 경우가 많다. 이 수치가 답하는 것은 "지금 유동성이 어느
 * 쪽에 얇은가" 하나이며, 얇은 쪽으로 가격이 빨리 움직이는 것은 예측이 아니라 체결의 성질이다.
 */
class OrderBookTest {

    private static final Instant AT = Instant.parse("2026-08-23T09:00:00Z");

    @Test
    void 최우선_호가는_매수의_최고가와_매도의_최저가다() {
        OrderBook book = 호가(List.of(단("100.00", "1"), 단("99.00", "2")),
                              List.of(단("101.00", "1"), 단("102.00", "2")));

        assertThat(book.bestBid().value()).isEqualByComparingTo("100.00");
        assertThat(book.bestAsk().value()).isEqualByComparingTo("101.00");
        assertThat(book.spread().value()).isEqualByComparingTo("1.00");
    }

    @Test
    void 스프레드_비율은_최우선_매도가_기준이다() {
        OrderBook book = 호가(List.of(단("99.00", "1")), List.of(단("100.00", "1")));

        // 1.00 / 100.00 = 1%
        assertThat(book.spreadPercent().value()).isEqualByComparingTo("1.0000");
    }

    @Test
    void 매수가_두꺼우면_불균형은_양수다() {
        OrderBook book = 호가(List.of(단("100.00", "3")), List.of(단("101.00", "1")));

        // (3 - 1) / (3 + 1) = 0.5
        assertThat(book.imbalance()).isEqualByComparingTo("0.5000");
    }

    @Test
    void 매도가_두꺼우면_불균형은_음수다() {
        OrderBook book = 호가(List.of(단("100.00", "1")), List.of(단("101.00", "3")));

        assertThat(book.imbalance()).isEqualByComparingTo("-0.5000");
    }

    @Test
    void 양쪽이_같으면_불균형은_0이다() {
        OrderBook book = 호가(List.of(단("100.00", "2")), List.of(단("101.00", "2")));

        assertThat(book.imbalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    void 잔량_합은_모든_단을_더한_것이다() {
        OrderBook book = 호가(
                List.of(단("100.00", "1.5"), 단("99.00", "2.25")),
                List.of(단("101.00", "0.75")));

        assertThat(book.bidVolume().value()).isEqualByComparingTo("3.75000000");
        assertThat(book.askVolume().value()).isEqualByComparingTo("0.75000000");
    }

    /**
     * <b>한쪽이 비면 스프레드를 말할 수 없다.</b> 0 으로 적으면 "호가가 붙어 있다" 로 읽히는데,
     * 실제로는 그 쪽에 주문이 하나도 없다는 정반대의 사실이다.
     */
    @Test
    void 한쪽이_비면_호가로_성립하지_않는다() {
        assertThatThrownBy(() -> 호가(List.of(), List.of(단("101.00", "1"))))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> 호가(List.of(단("100.00", "1")), List.of()))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 매도가_매수보다_낮을_수_없다() {
        assertThatThrownBy(() -> 호가(List.of(단("101.00", "1")), List.of(단("100.00", "1"))))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 넘어온_목록이_바뀌어도_호가는_그대로다() {
        List<PriceLevel> 매수 = new java.util.ArrayList<>(List.of(단("100.00", "1")));
        OrderBook book = new OrderBook(Symbol.of("BTCUSDT"), 매수, List.of(단("101.00", "1")), AT);

        매수.clear();

        assertThat(book.bids()).hasSize(1);
    }

    private static OrderBook 호가(List<PriceLevel> bids, List<PriceLevel> asks) {
        return new OrderBook(Symbol.of("BTCUSDT"), bids, asks, AT);
    }

    private static PriceLevel 단(String price, String quantity) {
        return new PriceLevel(Price.of(price), Quantity.of(quantity));
    }
}
