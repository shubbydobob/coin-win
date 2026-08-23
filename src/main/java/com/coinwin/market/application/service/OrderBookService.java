package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import org.springframework.stereotype.Service;

/**
 * 호가는 저장하지 않는다. 다음 순간이면 다른 값이기 때문이다.
 *
 * <p>그래서 이 서비스는 포트를 그대로 통과시킨다. <b>단수 검사가 여기 없는 것이 요점이다</b> —
 * 처음에는 "1 과 20 사이" 를 여기서 검사했는데, 그 범위는 우리가 상상한 것이고 거래소는
 * 정해진 값만 받는다({@code depth=3} 은 {@code -4021} 로 거절당한다). 규칙을 {@code
 * OrderBookDepth} 로 옮기니 인메모리 어댑터와 바이낸스 어댑터가 다르게 행동할 여지 자체가
 * 사라졌다.
 *
 * <p>통과시키기만 하는 층을 남겨 둔 이유는 {@code MarketMetricsService} 와 같다 — 캐싱이
 * 붙는다면 그 자리가 여기다.
 */
@Service
public class OrderBookService implements LoadOrderBookUseCase {

    private final LoadOrderBookPort port;

    public OrderBookService(LoadOrderBookPort port) {
        this.port = port;
    }

    @Override
    public OrderBook orderBook(Symbol symbol, OrderBookDepth depth) {
        return port.orderBookFor(symbol, depth);
    }

    @Override
    public Ticker ticker(Symbol symbol) {
        return port.tickerFor(symbol);
    }
}
