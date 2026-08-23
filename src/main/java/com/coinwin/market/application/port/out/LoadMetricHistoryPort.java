package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.Symbol;

/**
 * 지표의 지난 값들을 읽어 오는 곳. "지금이 평소와 얼마나 다른가" 는 이 표본 없이 답할 수 없다.
 *
 * <p>세 메서드로 나눈 것이 {@link LoadMarketMetricsPort} 와 다른 점이다. 그쪽은 <b>한 시점</b>의
 * 세 값이라 함께 읽어야 뜻이 맞지만, 이력은 각각 다른 주기(펀딩비 8시간, 나머지 5분)를 갖는
 * 서로 독립적인 시계열이다. 묶으면 한 지표의 이력이 없을 때 나머지 둘도 못 읽는다.
 */
public interface LoadMetricHistoryPort {

    MetricHistory fundingRates(Symbol symbol, int limit);

    MetricHistory openInterest(Symbol symbol, int limit);

    MetricHistory longShortRatios(Symbol symbol, int limit);
}
