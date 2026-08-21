package com.coinwin.backtest.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.ai.application.port.out.WriteSummaryPort;
import com.coinwin.ai.application.service.SummaryService;
import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.backtest.application.BacktestService;
import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.adapter.out.memory.InMemoryCandleAdapter;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.FixedMaintenanceMarginPolicy;
import java.util.Optional;
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
        mockMvc = mockMvcSummarizedBy(Optional.of(facts -> "거래가 없었다."));
    }

    /** 요약 어댑터를 갈아 끼운 컨트롤러. 빈 {@code Optional} 은 키가 없는 상태다. */
    private static MockMvc mockMvcSummarizedBy(Optional<WriteSummaryPort> writeSummary) {
        InMemoryCandleAdapter candles = new InMemoryCandleAdapter();
        candles.save(new Symbol("BTCUSDT"), CandleInterval.ONE_HOUR,
                BacktestFixtures.zigzag(30, 8, 59000, 61000));
        BacktestService backtests = new BacktestService(
                candles, new FixedMaintenanceMarginPolicy(Percentage.of("0.4")));
        return MockMvcBuilders
                .standaloneSetup(new BacktestController(
                        backtests, new SummaryService(writeSummary)))
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

    /** 요약과 함께 <b>그 요약이 쓸 수 있었던 수치 전부</b>가 내려와야 대조가 가능하다. */
    @Test
    void 요약은_원본_수치와_함께_내려온다() throws Exception {
        실행("/narrative", REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.narrative").value("거래가 없었다."))
                .andExpect(jsonPath("$.facts['거래 수']").exists())
                // jsonPath 표현식은 포맷 문자열로 처리된다. 레이블의 % 를 그대로 쓰면
                // 단언이 아니라 UnknownFormatConversionException 이 난다.
                .andExpect(jsonPath("$.facts['최대낙폭(%%)']").exists())
                .andExpect(jsonPath("$.conditions['종목']").value("BTCUSDT"));
    }

    /**
     * 원본에 없는 수를 쓴 요약은 응답이 되지 않는다. 사용자가 고칠 것이 없으므로 503 이다 —
     * 요약의 입력에는 사용자의 자유 텍스트가 한 글자도 없다.
     */
    @Test
    void 지어낸_수가_들어간_요약은_503이다() throws Exception {
        mockMvc = mockMvcSummarizedBy(Optional.of(facts -> "평단 64321.00 에서 잡았다."));

        실행("/narrative", REQUEST)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("64321")));
    }

    @Test
    void 키가_없으면_요약은_503이다() throws Exception {
        mockMvc = mockMvcSummarizedBy(Optional.<WriteSummaryPort>empty());

        실행("/narrative", REQUEST)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("OPENAI_API_KEY")));
    }
}
