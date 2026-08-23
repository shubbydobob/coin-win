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

    /**
     * 시장가로 때린 쪽의 비율.
     *
     * <p>롱숏비율과 다른 것을 센다 — 그쪽은 <b>계정 수</b> 라 1계정 1표이고, 이쪽은 <b>실제
     * 체결량</b> 이다. 호가는 취소되지만 체결은 되돌릴 수 없다.
     */
    MetricHistory takerRatios(Symbol symbol, int limit);

    /**
     * 상위 계정의 롱숏비. <b>포지션 크기 기준</b>이다.
     *
     * <p>계정 수 기준도 거래소가 주지만 그것은 이미 있는 {@link #longShortRatios} 와 같은 것을
     * 센다. 크기 기준만 "큰손이 어느 쪽에 얼마나" 를 말한다.
     */
    MetricHistory topPositionRatios(Symbol symbol, int limit);

    /**
     * 종가 시계열. <b>지표가 아니라 나란히 놓기 위한 기준선</b>이다.
     *
     * <p>미결제약정 −3.2% 는 가격 +1.1% 옆에서만 뜻이 된다. 같은 포트에 두는 이유는 같은
     * 주기·같은 구간이어야 나란히 놓는 것이 성립하기 때문이다 — 저장된 캔들(1시간·4시간·일)
     * 로는 30분 창을 만들 수 없다.
     */
    MetricHistory prices(Symbol symbol, int limit);
}
