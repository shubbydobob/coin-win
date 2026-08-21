package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;

/**
 * 캔들을 저장하는 곳. 구현체는 둘이다 — 영속화와 인메모리.
 *
 * <p>거래소 어댑터는 이 포트를 구현하지 않는다. 그래서 조회와 저장을 한 인터페이스에 묶지
 * 않았다. 묶었다면 {@code BinanceCandleAdapter} 가 구현할 수 없는 메서드를 떠안고
 * {@code UnsupportedOperationException} 을 던지게 된다.
 */
public interface SaveCandlesPort {

    /**
     * 캔들을 증분 저장한다. <b>이미 저장된 시각은 다시 세지 않는다.</b>
     *
     * @return 이번 호출로 <b>새로 </b>저장된 캔들 수. 전부 이미 있었다면 0.
     *     반환값이 개수인 이유는 Phase 3 완료 조건("증분 저장에 중복 없음")을 호출부에서
     *     그대로 단언할 수 있게 하기 위해서다. 같은 묶음을 두 번 저장하면 두 번째는 0 이다.
     */
    int save(Symbol symbol, CandleInterval interval, CandleSeries candles);
}
