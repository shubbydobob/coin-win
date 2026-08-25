package com.coinwin.readout.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.adapter.out.memory.InMemoryCandleAdapter;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.service.MarketDataService;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.readout.application.ReadoutService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 지표 곡선 엔드포인트의 HTTP 경계.
 *
 * <p><b>계산은 여기서 보지 않는다.</b> 값이 맞는지는 각 계산기의 골든 테스트가, 어느 지표가
 * 나오고 언제 비는지는 {@code TimeframeSeriesTest} 가 이미 증명했다. 이쪽이 보는 것은
 * <b>배선</b>이다 — 경로가 붙었나, 주기 파라미터가 도메인으로 넘어가나, 응답 모양이 스키마와
 * 같은가.
 *
 * <p>이것이 없으면 <b>"손으로 우회한 배선은 검증되지 않는다"</b> 가 그대로 반복된다.
 * Flyway 가 Phase 3 이후로 아무 일도 하지 않고 있던 것을 Phase 7 이 되어서야 알았다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는 이유는 {@code MarketControllerTest} 와 같다 —
 * 전체 컨텍스트를 띄우면 진짜 바이낸스 어댑터가 주입되어 HTTP 매핑을 보려는 테스트가
 * 네트워크에 매달린다.
 */
class ReadoutControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");

    private static final String SERIES = "/api/readout/BTCUSDT/series";

    /** 거래소 자리. 판독기는 읽기 전에 채우므로 여기가 비면 저장소도 빈다. */
    private static final class StubExchange implements LoadCandlesPort {
        private CandleSeries answer = CandleSeries.empty();

        @Override
        public CandleSeries load(CandleQuery query) {
            return answer.within(query.range());
        }
    }

    private MockMvc mvc;

    private StubExchange exchange;

    @BeforeEach
    void setUp() {
        InMemoryCandleAdapter store = new InMemoryCandleAdapter();
        exchange = new StubExchange();
        MarketDataService marketData = new MarketDataService(store, exchange, store);
        ReadoutService readout =
                new ReadoutService(marketData, marketData, Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new ReadoutController(readout))
                .setControllerAdvice(new DomainExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("봉이 넉넉하면 다섯 지표가 모두 실려 온다")
    void 다섯_지표가_실린다() throws Exception {
        exchange.answer = 봉(700);

        mvc.perform(get(SERIES).param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.count").value(700))
                .andExpect(jsonPath("$.candles[0].openTime").exists())
                .andExpect(jsonPath("$.ichimoku[0].conversionLine").exists())
                .andExpect(jsonPath("$.bollinger[0].upper").exists())
                .andExpect(jsonPath("$.rsi[0].value").exists())
                .andExpect(jsonPath("$.macd[0].histogram").exists())
                .andExpect(jsonPath("$.movingAverages.length()").value(5))
                .andExpect(jsonPath("$.movingAverages[0].period").value(10))
                .andExpect(jsonPath("$.movingAverages[4].period").value(300));
    }

    /**
     * <b>하나가 모자라다고 전부를 거절하지 않는다.</b> 응답 모양에서도 그래야 화면이
     * "그 지표만 없다" 를 말할 수 있다.
     */
    @Test
    @DisplayName("봉이 모자란 지표만 빈 목록으로 온다")
    void 모자란_지표만_빈다() throws Exception {
        exchange.answer = 봉(100);

        mvc.perform(get(SERIES).param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movingAverages[3].period").value(200))
                .andExpect(jsonPath("$.movingAverages[3].points.length()").value(0))
                .andExpect(jsonPath("$.movingAverages[0].points.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.rsi.length()").value(Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("모르는 주기는 400 이다")
    void 모르는_주기() throws Exception {
        exchange.answer = 봉(300);

        mvc.perform(get(SERIES).param("interval", "3분")).andExpect(status().isBadRequest());
    }

    /** 톱니. 단조로 흐르면 RSI 가 100 에 붙어 "값이 있다" 만 보게 된다. */
    private static CandleSeries 봉(int count) {
        Instant start = NOW.minus(Duration.ofHours(count));
        return new CandleSeries(IntStream.range(0, count).mapToObj(i -> {
            BigDecimal close = new BigDecimal(60000 + (i % 11) * 130 + i * 7);
            return new Candle(
                    start.plus(Duration.ofHours(i)),
                    Price.of(close),
                    Price.of(close.add(new BigDecimal("120"))),
                    Price.of(close.subtract(new BigDecimal("120"))),
                    Price.of(close),
                    Quantity.of(BigDecimal.ONE));
        }).toList());
    }
}
