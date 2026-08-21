package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.CandleQuery;

/**
 * 거래소에서 캔들을 받아 저장한다. 읽기와 <b>분리한</b> 이유가 이 인터페이스의 존재 이유다.
 *
 * <p>조회할 때마다 거래소를 때리면 두 가지가 깨진다. 첫째, 같은 질의가 같은 답을 내지 않게
 * 되어 Phase 6 백테스트의 재현성이 사라진다. 둘째, 네트워크가 끊기면 이미 저장해 둔
 * 데이터마저 읽지 못한다.
 *
 * <p>그래서 {@link LoadMarketDataUseCase} 는 저장된 것만 읽고, 채우는 것은 이쪽이 한다.
 * 언제 채울지는 사람이 정한다.
 */
public interface SyncMarketDataUseCase {

    /**
     * 구간의 캔들을 거래소에서 받아 저장한다.
     *
     * @return 새로 저장된 캔들 수. 이미 다 있었다면 0.
     */
    int sync(CandleQuery query);
}
