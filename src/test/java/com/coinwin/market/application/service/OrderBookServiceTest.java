package com.coinwin.market.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.market.adapter.out.memory.InMemoryOrderBookAdapter;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 서비스가 하는 유일한 판단은 단수 상한이다.
 *
 * <p>DB 도 거래소도 없이 돈다 — 인메모리 어댑터가 있는 이유가 그것이다.
 */
class OrderBookServiceTest {

    private static final Symbol SYMBOL = Symbol.of("BTCUSDT");

    private final OrderBookService service = new OrderBookService(
            InMemoryOrderBookAdapter.withSample(SYMBOL, Instant.parse("2026-08-23T09:00:00Z")));

    @Test
    void 상한_안의_단수는_그대로_통과한다() {
        assertThat(service.orderBook(SYMBOL, 5).bids()).hasSize(5);
        assertThat(service.orderBook(SYMBOL, OrderBookService.MAXIMUM_DEPTH).bids()).hasSize(20);
    }

    /**
     * 상한을 넘기면 <b>깎지 않고 거부한다.</b> 조용히 20 으로 줄이면 부른 쪽은 자기가 요청한
     * 깊이를 받았다고 믿고, 불균형을 다른 기준으로 읽는다.
     */
    @Test
    void 상한을_넘는_단수는_거부한다() {
        assertThatThrownBy(() -> service.orderBook(SYMBOL, 21))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> service.orderBook(SYMBOL, 0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 시세는_그대로_통과시킨다() {
        assertThat(service.ticker(SYMBOL).symbol()).isEqualTo(SYMBOL);
    }
}
