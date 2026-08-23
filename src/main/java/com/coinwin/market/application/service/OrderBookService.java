package com.coinwin.market.application.service;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import org.springframework.stereotype.Service;

/**
 * 호가는 저장하지 않는다. 다음 순간이면 다른 값이기 때문이다.
 *
 * <p>이 서비스가 하는 유일한 판단은 <b>단수 상한</b>이다. 20 을 넘기지 않는 이유는 둘이다 —
 * 더 깊이 가면 실제로 체결될 일 없는 주문이 섞여 불균형이 흐려지고, 20 이 거래소 가중치가
 * 낮게 유지되는 상한이기도 하다. 그 판단이 어댑터에 있으면 어댑터를 바꿀 때 정책이 따라간다.
 */
@Service
public class OrderBookService implements LoadOrderBookUseCase {

    /** 이보다 깊이 보지 않는다. 근거: {@code docs/spec/market-watch.md} § 3.2 */
    public static final int MAXIMUM_DEPTH = 20;

    private final LoadOrderBookPort port;

    public OrderBookService(LoadOrderBookPort port) {
        this.port = port;
    }

    @Override
    public OrderBook orderBook(Symbol symbol, int depth) {
        if (depth < 1 || depth > MAXIMUM_DEPTH) {
            throw new InvalidValueException(
                    "호가 단수는 1 과 " + MAXIMUM_DEPTH + " 사이여야 한다: " + depth);
        }
        return port.orderBookFor(symbol, depth);
    }

    @Override
    public Ticker ticker(Symbol symbol) {
        return port.tickerFor(symbol);
    }
}
