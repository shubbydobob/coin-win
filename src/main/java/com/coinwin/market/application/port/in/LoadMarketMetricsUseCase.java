package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;

/** 한 시점의 펀딩비·미결제약정·롱숏비율. */
public interface LoadMarketMetricsUseCase {

    MarketMetrics metrics(Symbol symbol);
}
