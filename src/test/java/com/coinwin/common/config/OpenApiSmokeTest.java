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

    @Test
    void 요청과_응답_스키마에_예제가_붙어_있다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.AnalyzePositionRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.PositionAnalysisResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.MonteCarloRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.MonteCarloResponse.example").exists())
                .andExpect(jsonPath("$.components.schemas.EquityCurveRequest.example").exists())
                .andExpect(jsonPath("$.components.schemas.EquityCurveResponse.example").exists());
    }
}
