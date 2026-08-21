package com.coinwin.market.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.market.MarketFixtures;
import com.coinwin.market.adapter.out.memory.InMemoryCandleAdapter;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 조율만 하는 서비스이므로 검사할 것도 하나다 — <b>조회가 거래소를 때리지 않는가.</b>
 *
 * <p>이것이 성질 이상의 문제인 이유: 조회가 매번 거래소를 때리면 같은 질의가 같은 답을 내지
 * 않고, Phase 6 의 "동일 파라미터 재실행 시 결과 완전 동일" 이 그 자리에서 무너진다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 인메모리 어댑터가 있는 이유가 정확히 이것이다.
 */
class MarketDataServiceTest {

    /** 몇 번 불렸는지 세는 거래소. 0 이어야 한다는 것을 단언하려면 세는 수밖에 없다. */
    private static final class CountingExchange implements LoadCandlesPort {
        private final AtomicInteger calls = new AtomicInteger();
        private CandleSeries answer = CandleSeries.empty();

        @Override
        public CandleSeries load(CandleQuery query) {
            calls.incrementAndGet();
            return answer.within(query.range());
        }
    }

    private CountingExchange exchange;
    private InMemoryCandleAdapter store;
    private MarketDataService service;

    @BeforeEach
    void 서비스를_조립한다() {
        exchange = new CountingExchange();
        store = new InMemoryCandleAdapter();
        service = new MarketDataService(store, exchange, store);
    }

    @Test
    void 조회는_거래소를_한_번도_때리지_않는다() {
        store.save(MarketFixtures.SYMBOL, MarketFixtures.INTERVAL, MarketFixtures.candles(0, 5));

        CandleSeries loaded = service.candles(MarketFixtures.query(0, 5));

        assertThat(loaded.size()).isEqualTo(5);
        assertThat(exchange.calls).hasValue(0);
    }

    @Test
    void 저장된_것이_없으면_조회는_빈_묶음을_돌려준다() {
        assertThat(service.candles(MarketFixtures.query(0, 5)).isEmpty()).isTrue();
        assertThat(exchange.calls).hasValue(0);
    }

    @Test
    void 수집은_거래소에서_받아_저장한다() {
        exchange.answer = MarketFixtures.candles(0, 5);

        assertThat(service.sync(MarketFixtures.query(0, 5))).isEqualTo(5);
        assertThat(service.candles(MarketFixtures.query(0, 5)).size()).isEqualTo(5);
    }

    /** 완료 조건 "증분 저장에 중복 없음" 이 서비스 층에서도 그대로 보여야 한다. */
    @Test
    void 같은_구간을_두_번_수집하면_두_번째는_0건이다() {
        exchange.answer = MarketFixtures.candles(0, 5);

        assertThat(service.sync(MarketFixtures.query(0, 5))).isEqualTo(5);
        assertThat(service.sync(MarketFixtures.query(0, 5))).isZero();
        assertThat(service.candles(MarketFixtures.query(0, 5)).size()).isEqualTo(5);
    }

    @Test
    void 겹치는_구간을_이어_수집해도_중복이_생기지_않는다() {
        exchange.answer = MarketFixtures.candles(0, 10);

        assertThat(service.sync(MarketFixtures.query(0, 5))).isEqualTo(5);
        assertThat(service.sync(MarketFixtures.query(3, 8))).isEqualTo(3);
        assertThat(service.candles(MarketFixtures.query(0, 10)).size()).isEqualTo(8);
    }
}
