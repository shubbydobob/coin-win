package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;

/**
 * 캔들을 얻는다. 어디서 오는지는 호출부의 관심이 아니다.
 *
 * <p>저장된 것으로 충분하면 저장된 것을, 모자라면 거래소에서 받아 채운 뒤 돌려준다.
 * 이 판단이 {@code application.service} 에 있는 이유는 그것이 <b>정책</b>이기 때문이다.
 * 어댑터는 각자 자기 저장소만 안다.
 */
public interface LoadMarketDataUseCase {

    CandleSeries candles(CandleQuery query);
}
