package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;

/**
 * 지금 이 순간의 호가와 시세를 읽어 오는 곳.
 *
 * <p><b>둘을 한 포트에 두는 이유</b>는 같은 질문의 두 면이기 때문이다 — "지금 얼마인가" 와
 * "그 값에 얼마나 두껍게 쌓여 있는가". 화면이 언제나 함께 읽으므로 따로 두면 서로 다른 시각의
 * 값을 나란히 놓게 된다. {@link LoadMarketMetricsPort} 가 세 지표를 한 시각으로 묶은 것과 같은
 * 판단이다.
 *
 * <p><b>이력은 여기 없다.</b> {@link LoadMetricHistoryPort} 가 따로 있는 이유는 갱신 주기가
 * 다르기 때문이다 — 호가는 3초, 이력은 5분이다. 한 포트에 몰면 3초 폴링이 이력까지 매번
 * 끌어온다.
 */
public interface LoadOrderBookPort {

    /** 호가 {@code depth} 단. 20 을 넘기지 않는다 — 더 깊이 가면 체결될 일 없는 주문이 섞인다. */
    OrderBook orderBookFor(Symbol symbol, int depth);

    Ticker tickerFor(Symbol symbol);
}
