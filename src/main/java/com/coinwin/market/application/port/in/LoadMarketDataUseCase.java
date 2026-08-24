package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;

/**
 * 캔들을 얻는다. 어디서 오는지는 호출부의 관심이 아니다.
 *
 * <p><b>저장된 것만 읽는다. 거래소를 때리지 않는다.</b> 채우는 것은
 * {@link SyncMarketDataUseCase} 의 일이고, 둘을 나눈 덕분에 백테스트가 결정론을 갖는다 —
 * 같은 구간을 다시 돌리면 그 사이 거래소에 무슨 일이 있었든 같은 캔들을 읽는다
 * ({@code BacktestSpec} 이 그 사실에 기대고 있다).
 *
 * <p><b>이 문단은 한 번 거짓말이었다.</b> 원래 "저장된 것으로 충분하면 저장된 것을, 모자라면
 * 거래소에서 받아 채운 뒤 돌려준다" 고 적혀 있었는데 구현은 처음부터 저장된 것만 읽는 한
 * 줄이었다. 소비자가 {@code market} 안에만 있는 동안에는 아무도 몰랐고, 밖에서 처음 쓴
 * 판독기가 <b>빈 목록을 받아 500 으로 죽으면서</b> 드러났다. 스키마가 그랬듯 문서도
 * 검사하는 것이 없으면 코드와 갈라진다.
 */
public interface LoadMarketDataUseCase {

    CandleSeries candles(CandleQuery query);
}
