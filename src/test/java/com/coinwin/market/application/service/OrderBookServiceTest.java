package com.coinwin.market.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.market.adapter.out.memory.InMemoryOrderBookAdapter;
import com.coinwin.market.domain.OrderBookDepth;
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
    void 허용된_단수는_그대로_통과한다() {
        assertThat(service.orderBook(SYMBOL, OrderBookDepth.of(5)).bids()).hasSize(5);
        assertThat(service.orderBook(SYMBOL, OrderBookDepth.DEFAULT).bids()).hasSize(20);
    }

    /**
     * <b>거래소가 정한 값만 받는다.</b> {@code depth=3} 은 바이낸스가 {@code -4021} 로 거절한다.
     * 처음에는 서비스가 "1 과 20 사이" 로 검사했는데 그 범위는 우리가 상상한 것이었고, 실제로
     * 3 을 보내 보고서야 드러났다. 규칙이 타입으로 내려가면서 <b>인메모리와 바이낸스가 다르게
     * 행동할 여지</b>도 함께 사라졌다.
     */
    @Test
    void 거래소가_받지_않는_단수는_만들_수조차_없다() {
        assertThatThrownBy(() -> OrderBookDepth.of(3)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> OrderBookDepth.of(21)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> OrderBookDepth.of(0)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 시세는_그대로_통과시킨다() {
        assertThat(service.ticker(SYMBOL).symbol()).isEqualTo(SYMBOL);
    }
}
