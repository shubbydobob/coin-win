package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import com.coinwin.market.application.port.out.ExchangeCandles;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPort;
import com.coinwin.market.application.port.out.StoredCandles;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import org.springframework.stereotype.Service;

/**
 * 읽기와 채우기를 잇는 조율자. 계산은 하지 않는다.
 *
 * <p>읽기는 <b>저장된 것만</b> 본다. 조회할 때마다 거래소를 때리면 같은 질의가 같은 답을 내지
 * 않게 되고, Phase 6 백테스트의 "동일 파라미터 재실행 시 결과 완전 동일" 이 성립하지 않는다.
 *
 * <p>이 서비스가 아는 것은 포트 세 개뿐이다. 어느 것이 PostgreSQL 이고 어느 것이 바이낸스인지
 * 모른다 — 그것이 {@link StoredCandles} / {@link ExchangeCandles} 를 빈 이름 대신 쓴 이유다.
 */
@Service
public class MarketDataService implements LoadMarketDataUseCase, SyncMarketDataUseCase {

    private final LoadCandlesPort storedCandles;
    private final LoadCandlesPort exchangeCandles;
    private final SaveCandlesPort candleStore;

    public MarketDataService(
            @StoredCandles LoadCandlesPort storedCandles,
            @ExchangeCandles LoadCandlesPort exchangeCandles,
            SaveCandlesPort candleStore) {
        this.storedCandles = storedCandles;
        this.exchangeCandles = exchangeCandles;
        this.candleStore = candleStore;
    }

    @Override
    public CandleSeries candles(CandleQuery query) {
        return storedCandles.load(query);
    }

    /**
     * {@inheritDoc}
     *
     * <p>받아 온 것을 그대로 저장한다. 무엇이 이미 있는지 여기서 따지지 않는 이유는, 그 판단이
     * 저장소의 것이기 때문이다. {@link SaveCandlesPort} 가 기본키로 걸러내고 새로 들어간 수만
     * 돌려준다.
     */
    @Override
    public int sync(CandleQuery query) {
        CandleSeries fetched = exchangeCandles.load(query);
        return candleStore.save(query.symbol(), query.interval(), fetched);
    }
}
