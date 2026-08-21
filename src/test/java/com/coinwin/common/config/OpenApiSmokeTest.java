package com.coinwin.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * springdoc 이 실제로 문서를 만들어 내는지 확인하는 스모크 테스트.
 *
 * <p>Phase 1 부터는 엔드포인트가 실제로 문서에 실리는지까지 본다. 컨트롤러가 동작해도
 * 문서화 애너테이션이 빠지면 조용히 비어 있는 스펙이 나가기 때문이다.
 */
@SpringBootTest
class OpenApiSmokeTest {

    private static final String ANALYSIS_OPERATION = "$.paths['/api/position-plans/analysis'].post";

    private static final String MONTE_CARLO_OPERATION = "$.paths['/api/projections/monte-carlo'].post";

    private static final String CANDLES_OPERATION =
            "$.paths['/api/markets/{symbol}/candles'].get";

    private static final String SYNC_OPERATION =
            "$.paths['/api/markets/{symbol}/candles/sync'].post";

    private static final String METRICS_OPERATION =
            "$.paths['/api/markets/{symbol}/metrics'].get";

    private static final String PLAN_TRADE_OPERATION = "$.paths['/api/trades'].post";

    private static final String FILLS_OPERATION = "$.paths['/api/trades/{id}/fills'].post";

    private static final String CLOSURE_OPERATION = "$.paths['/api/trades/{id}/closure'].post";

    private static final String SUMMARY_OPERATION = "$.paths['/api/trades/summary'].get";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void api_문서가_생성되고_프로젝트_제목을_담는다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CoinWin API"));
    }

    @Test
    void 포지션_계획_ANALYSIS_OPERATION가_요약과_오류_응답까지_문서화된다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ANALYSIS_OPERATION + ".summary").exists())
                .andExpect(jsonPath(ANALYSIS_OPERATION + ".responses.['400'].description").exists())
                .andExpect(jsonPath(ANALYSIS_OPERATION + ".responses.['422'].description").exists());
    }

    @Test
    void 복리_시뮬레이션_MONTE_CARLO_OPERATION도_요약과_오류_응답까지_문서화된다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/projections/equity-curve'].post.summary").exists())
                .andExpect(jsonPath(MONTE_CARLO_OPERATION + ".summary").exists())
                .andExpect(jsonPath(MONTE_CARLO_OPERATION + ".responses.['400'].description").exists())
                .andExpect(jsonPath(MONTE_CARLO_OPERATION + ".responses.['422'].description").exists());
    }

    /**
     * 이 테스트가 초록이라는 것은 문서만이 아니라 <b>컨텍스트 전체가 뜬다</b>는 뜻이기도 하다.
     * Phase 3 에서 캔들 어댑터·바이낸스 클라이언트·시장 데이터 서비스가 붙었으므로, 주입이
     * 어긋나면(같은 포트 구현체가 둘인데 한정자가 없다든지) 여기서 먼저 깨진다.
     */
    @Test
    void 시장_데이터_엔드포인트가_문서화된다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CANDLES_OPERATION + ".summary").exists())
                .andExpect(jsonPath(CANDLES_OPERATION + ".responses.['400'].description").exists())
                .andExpect(jsonPath(SYNC_OPERATION + ".summary").exists())
                .andExpect(jsonPath(SYNC_OPERATION + ".responses.['503'].description").exists())
                .andExpect(jsonPath(METRICS_OPERATION + ".summary").exists())
                .andExpect(jsonPath(METRICS_OPERATION + ".responses.['503'].description").exists());
    }

    /**
     * Phase 5 에서 JPA 어댑터·QueryDSL 팩토리·시계 빈이 붙었다. 이 테스트가 초록이라는 것은
     * 그 셋이 실제로 조립된다는 뜻이다 — 같은 포트를 구현한 인메모리 어댑터가 컨텍스트에
     * 함께 올라가 주입이 갈리는 상황이 없는지까지 여기서 걸린다.
     */
    @Test
    void 매매_기록_엔드포인트가_문서화된다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PLAN_TRADE_OPERATION + ".summary").exists())
                .andExpect(jsonPath(PLAN_TRADE_OPERATION + ".responses.['422'].description").exists())
                .andExpect(jsonPath(FILLS_OPERATION + ".summary").exists())
                .andExpect(jsonPath(FILLS_OPERATION + ".responses.['404'].description").exists())
                .andExpect(jsonPath(CLOSURE_OPERATION + ".summary").exists())
                .andExpect(jsonPath(CLOSURE_OPERATION + ".responses.['404'].description").exists())
                .andExpect(jsonPath(SUMMARY_OPERATION + ".summary").exists());
    }

    @Test
    void 요청과_응답_스키마에_예제가_붙어_있다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.AnalyzePositionRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.PositionAnalysisResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.MonteCarloRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.MonteCarloResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.EquityCurveRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.EquityCurveResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.TradePlanRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.RecordFillsRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.CloseTradeRequest.example").exists());
    }
}
