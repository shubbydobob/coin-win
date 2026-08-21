package com.coinwin.backtest.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.backtest.application.BacktestService;
import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.adapter.out.memory.InMemoryCandleAdapter;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.FixedMaintenanceMarginPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP 경계 검증. 계산은 도메인 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태 코드</b>만 본다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는 이유는 {@code TradeJournalControllerTest} 와 같다 —
 * 전체 컨텍스트를 띄우면 진짜 어댑터가 주입되어 HTTP 매핑을 보려는 테스트가 DB 에 매달린다.
 * 캔들은 인메모리 어댑터에 직접 심는다.
 */
class BacktestControllerTest {

    private static final String BACKTESTS = "/api/backtests";

    private static final String REQUEST = """
            {
              "symbol": "BTCUSDT",
              "interval": "1h",
              "from": "2026-08-01T00:00:00Z",
              "to": "2026-09-01T00:00:00Z",
              "zones": {
                "pivotLookback": 5, "clusterMultiple": 0.5,
                "minTouches": 2, "atrPeriod": 14
              },
              "rules": {
                "stopBufferMultiple": 1.0, "minRiskReward": 1.5, "indicatorFilter": false
              },
              "account": {
                "initialCapital": 800, "riskPercent": 2,
                "leverage": 10, "capitalMode": "FIXED"
              }
            }""";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InMemoryCandleAdapter candles = new InMemoryCandleAdapter();
        candles.save(new Symbol("BTCUSDT"), CandleInterval.ONE_HOUR,
                BacktestFixtures.zigzag(30, 8, 59000, 61000));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BacktestController(new BacktestService(
                        candles, new FixedMaintenanceMarginPolicy(Percentage.of("0.4")))))
                .setControllerAdvice(new DomainExceptionHandler())
                .build();
    }

    private org.springframework.test.web.servlet.ResultActions 실행(String path, String body)
            throws Exception {
        return mockMvc.perform(post(BACKTESTS + path)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 백테스트를_실행하면_요약과_거래와_자산곡선이_돌아온다() throws Exception {
        실행("", REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalTrades").value(greaterThan(0)))
                .andExpect(jsonPath("$.trades").isArray())
                .andExpect(jsonPath("$.trades[0].exitReason").value(
                        containsString("PLANNED_")))
                .andExpect(jsonPath("$.trades[0].rationale").value(containsString("대")))
                .andExpect(jsonPath("$.equityCurve[0]").value(800.00));
    }

    /** 진 거래가 없으면 손익비 자리가 {@code null} 이다. 큰 수를 지어내지 않는다. */
    @Test
    void 손실이_없으면_손익비는_null_로_나간다() throws Exception {
        실행("", REQUEST).andExpect(jsonPath("$.summary.profitFactor").doesNotExist());
    }

    @Test
    void 필터_온오프_비교는_두_실행을_나란히_돌려준다() throws Exception {
        실행("/indicator-filter-comparison", REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline.summary.totalTrades").exists())
                .andExpect(jsonPath("$.variant.summary.totalTrades").exists())
                .andExpect(jsonPath("$.tradeDifference").exists());
    }

    @Test
    void 비용_비교는_비용을_뺀_쪽이_기준이다() throws Exception {
        실행("/cost-comparison", REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnlDifference").exists());
    }

    /**
     * 터치 1회는 <b>값 자체가 범위를 벗어난 것</b>이라 400 이다.
     *
     * <p>422 는 값이 각각 유효한데 조합이 성립하지 않을 때다 — 이 코드베이스에서 그 구분은
     * {@code InvalidValueException} 과 나머지 {@code DomainException} 의 차이로 나타난다.
     */
    @Test
    void 터치_2회_미만의_대_설정은_400이다() throws Exception {
        실행("", REQUEST.replace("\"minTouches\": 2", "\"minTouches\": 1"))
                .andExpect(status().isBadRequest());
    }

    /** 알 수 없는 주기는 값 자체가 부적절하다. 400 이다. */
    @Test
    void 알_수_없는_캔들_주기는_400이다() throws Exception {
        실행("", REQUEST.replace("\"interval\": \"1h\"", "\"interval\": \"7초\""))
                .andExpect(status().isBadRequest());
    }
}
