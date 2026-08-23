package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;

/** 지금 이 순간의 호가와 시세. */
public interface LoadOrderBookUseCase {

    OrderBook orderBook(Symbol symbol, OrderBookDepth depth);

    Ticker ticker(Symbol symbol);
}
