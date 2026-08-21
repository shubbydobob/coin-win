package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;

/**
 * 펀딩비·미결제약정·롱숏비율을 읽어 오는 곳.
 *
 * <p>세 값의 출처가 각각 다른 엔드포인트인 것은 <b>어댑터의 사정</b>이다. 포트는 한 시점의
 * 시장 상태 하나만 약속한다. 세 메서드로 나누면 호출부가 세 번 호출한 뒤 서로 다른 시각의
 * 값을 나란히 놓게 된다.
 */
public interface LoadMarketMetricsPort {

    MarketMetrics metricsFor(Symbol symbol);
}
