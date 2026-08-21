package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadMarketMetricsUseCase;
import com.coinwin.market.application.port.out.LoadMarketMetricsPort;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;
import org.springframework.stereotype.Service;

/**
 * 시장 지표는 저장하지 않는다. 지금 값이 아니면 의미가 없기 때문이다.
 *
 * <p>그래서 이 서비스는 포트를 그대로 통과시킨다. 통과시키기만 하는 층을 남겨 둔 이유는
 * 캐싱이 붙을 자리가 여기이기 때문이다 — 어댑터에 캐싱을 넣으면 "얼마나 오래된 값을 허용할
 * 것인가" 라는 <b>정책</b>이 HTTP 계층으로 내려간다.
 */
@Service
public class MarketMetricsService implements LoadMarketMetricsUseCase {

    private final LoadMarketMetricsPort metricsPort;

    public MarketMetricsService(LoadMarketMetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    @Override
    public MarketMetrics metrics(Symbol symbol) {
        return metricsPort.metricsFor(symbol);
    }
}
