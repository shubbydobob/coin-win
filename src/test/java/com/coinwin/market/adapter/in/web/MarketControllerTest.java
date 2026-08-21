package com.coinwin.market.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.MarketFixtures;
import com.coinwin.market.adapter.out.memory.InMemoryCandleAdapter;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.LoadMarketMetricsPort;
import com.coinwin.market.application.service.MarketDataService;
import com.coinwin.market.application.service.MarketMetricsService;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.FundingRate;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP 경계 검증. 계산은 도메인과 서비스 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태
 * 코드</b>만 본다.
 *
 * <p>다른 모듈의 컨트롤러 테스트와 달리 {@code @SpringBootTest} 를 쓰지 않는다. 전체 컨텍스트를
 * 띄우면 진짜 PostgreSQL 어댑터와 진짜 바이낸스 어댑터가 주입되어, HTTP 매핑을 보려는 테스트가
 * DB 와 네트워크에 매달린다. 인메모리 어댑터가 있는 이유가 정확히 이것이다.
 */
class MarketControllerTest {

    private static final String CANDLES = "/api/markets/BTCUSDT/candles";
    private static final String FROM = MarketFixtures.hour(0).toString();
    private static final String TO = MarketFixtures.hour(5).toString();

    /** 거래소 자리. 테스트마다 무엇을 돌려줄지 갈아 끼운다. */
    private static final class StubExchange implements LoadCandlesPort {
        private CandleSeries answer = CandleSeries.empty();

        @Override
        public CandleSeries load(CandleQuery query) {
            return answer.within(query.range());
        }
    }

    private static final class StubMetrics implements LoadMarketMetricsPort {
        private boolean reachable = true;

        @Override
        public MarketMetrics metricsFor(Symbol symbol) {
            if (!reachable) {
                throw new ExternalDataUnavailableException("거래소에 닿지 못했다", null);
            }
            return new MarketMetrics(symbol, Instant.parse("2026-08-21T08:00:00Z"),
                    FundingRate.ofPercent("0.01"), Quantity.of("81234.5"),
                    new BigDecimal("1.8342"));
        }
    }

    private InMemoryCandleAdapter store;
    private StubExchange exchange;
    private StubMetrics metrics;
    private MockMvc mockMvc;

    @BeforeEach
    void 컨트롤러를_조립한다() {
        store = new InMemoryCandleAdapter();
        exchange = new StubExchange();
        metrics = new StubMetrics();
        MarketDataService marketData = new MarketDataService(store, exchange, store);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MarketController(
                        marketData, marketData, new MarketMetricsService(metrics)))
                .setControllerAdvice(new DomainExceptionHandler())
                .build();
    }

    @Test
    void 저장된_캔들을_조회하면_스케일_그대로_실린다() throws Exception {
        store.save(MarketFixtures.SYMBOL, MarketFixtures.INTERVAL, MarketFixtures.candles(0, 2));

        mockMvc.perform(get(CANDLES).param("interval", "1h").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.candles[0].open").value(60000.00))
                .andExpect(jsonPath("$.candles[0].volume").value(1.5));
    }

    @Test
    void 저장된_것이_없으면_빈_목록과_0건이_나간다() throws Exception {
        mockMvc.perform(get(CANDLES).param("interval", "1h").param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.candles").isEmpty());
    }

    /** 완료 조건이 API 응답에서 그대로 읽혀야 한다 — 두 번째 수집의 newlyStored 는 0. */
    @Test
    void 같은_구간을_두_번_수집하면_두_번째_응답은_0건이다() throws Exception {
        exchange.answer = MarketFixtures.candles(0, 5);

        수집().andExpect(status().isOk()).andExpect(jsonPath("$.newlyStored").value(5));
        수집().andExpect(status().isOk()).andExpect(jsonPath("$.newlyStored").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions 수집() throws Exception {
        return mockMvc.perform(post(CANDLES + "/sync")
                .param("interval", "1h").param("from", FROM).param("to", TO));
    }

    @Test
    void 모르는_캔들_주기는_400이다() throws Exception {
        mockMvc.perform(get(CANDLES).param("interval", "7m").param("from", FROM).param("to", TO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("7m")));
    }

    /** 값은 각각 유효한데 구간으로 성립하지 않는다. 400 이 아니라 422 다. */
    @Test
    void 끝이_시작보다_앞인_구간은_422다() throws Exception {
        mockMvc.perform(get(CANDLES).param("interval", "1h").param("from", TO).param("to", FROM))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 시장_지표는_한_시점으로_묶여_나간다() throws Exception {
        mockMvc.perform(get("/api/markets/BTCUSDT/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.at").value("2026-08-21T08:00:00Z"))
                .andExpect(jsonPath("$.fundingRatePercent").value(0.01))
                .andExpect(jsonPath("$.openInterest").value(81234.5))
                .andExpect(jsonPath("$.longShortRatio").value(1.8342));
    }

    /** 거래소가 닿지 않는 것은 500 이 아니다. 잠시 뒤 다시 하면 되는 일이라 503 이다. */
    @Test
    void 거래소가_닿지_않으면_503이다() throws Exception {
        metrics.reachable = false;

        mockMvc.perform(get("/api/markets/BTCUSDT/metrics"))
                .andExpect(status().isServiceUnavailable());
    }
}
