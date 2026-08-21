package com.coinwin.projection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTP 경계 검증. 계산은 도메인 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태 코드</b>,
 * 그리고 <b>같은 시드가 같은 응답을 낸다</b>는 약속만 본다.
 */
@SpringBootTest
class ProjectionControllerTest {

    private static final String SHORT_CURVE_REQUEST = """
            {
              "spec": {
                "initialCapital": 800,
                "winRate": 45,
                "riskRewardRatio": 2,
                "riskPerTrade": 2,
                "tradesPerWeek": 1,
                "weeks": 4
              },
              "seed": 20260821
            }""";

    private static final String MONTE_CARLO_INPUT = """
            {
              "spec": {
                "initialCapital": 800,
                "winRate": 45,
                "riskRewardRatio": 2,
                "riskPerTrade": 2,
                "tradesPerWeek": 2,
                "weeks": 50
              },
              "runs": 1000,
              "seed": 20260821
            }""";

    @Autowired
    private WebApplicationContext context;

    private ResultActions 요청(String 경로, String body) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/projections/" + 경로)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void 조건을_보내면_거래_수만큼의_점을_가진_자산_곡선이_온다() throws Exception {
        요청("equity-curve", SHORT_CURVE_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades").value(4))
                // 첫 점은 거래 이전의 초기 자본이므로 점은 거래 수보다 하나 많다
                .andExpect(jsonPath("$.equity.length()").value(5))
                .andExpect(jsonPath("$.equity[0]").value(800.00));
    }

    /** 재현성이 이 도구의 전제다. 같은 시드가 다른 답을 내면 두 조건을 비교할 수 없다. */
    @Test
    void 같은_시드로_두_번_요청하면_같은_곡선이_온다() throws Exception {
        String 첫번째 = 요청("equity-curve", SHORT_CURVE_REQUEST)
                .andReturn().getResponse().getContentAsString();
        String 두번째 = 요청("equity-curve", SHORT_CURVE_REQUEST)
                .andReturn().getResponse().getContentAsString();

        assertThat(첫번째).isEqualTo(두번째);
    }

    @Test
    void 시드가_다르면_다른_곡선이_온다() throws Exception {
        String 다른_시드 = SHORT_CURVE_REQUEST.replace("\"seed\": 20260821", "\"seed\": 20260822");

        assertThat(요청("equity-curve", SHORT_CURVE_REQUEST).andReturn().getResponse().getContentAsString())
                .isNotEqualTo(요청("equity-curve", 다른_시드)
                        .andReturn().getResponse().getContentAsString());
    }

    @Test
    void 몬테카를로는_백분위와_낙폭_분포를_돌려준다() throws Exception {
        요청("monte-carlo", MONTE_CARLO_INPUT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs").value(1000))
                .andExpect(jsonPath("$.tradesPerRun").value(100))
                .andExpect(jsonPath("$.expectancyPerTrade").value(0.350000))
                .andExpect(jsonPath("$.worstEquity").exists())
                .andExpect(jsonPath("$.percentile5Equity").exists())
                .andExpect(jsonPath("$.medianEquity").exists())
                .andExpect(jsonPath("$.percentile95Equity").exists())
                .andExpect(jsonPath("$.bestEquity").exists())
                .andExpect(jsonPath("$.medianMaxDrawdown").exists())
                .andExpect(jsonPath("$.worstMaxDrawdown").exists())
                .andExpect(jsonPath("$.lossProbability").exists());
    }

    /** 문서의 예제와 실제 응답이 갈라지면 예제는 거짓말이 된다. 같은 요청, 같은 시드다. */
    @Test
    void 문서에_실린_예제_응답이_실제_응답과_일치한다() throws Exception {
        String 곡선 = 요청("equity-curve", ProjectionApiExamples.CURVE_REQUEST)
                .andReturn().getResponse().getContentAsString();
        String 분포 = 요청("monte-carlo", ProjectionApiExamples.MONTE_CARLO_REQUEST)
                .andReturn().getResponse().getContentAsString();

        assertThat(정규화(곡선)).isEqualTo(정규화(ProjectionApiExamples.CURVE_RESPONSE));
        assertThat(정규화(분포)).isEqualTo(정규화(ProjectionApiExamples.MONTE_CARLO_RESPONSE));
    }

    @Test
    void 값_자체가_부적절하면_400() throws Exception {
        String 승률_101 = SHORT_CURVE_REQUEST.replace("\"winRate\": 45", "\"winRate\": 101");

        요청("equity-curve", 승률_101)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("값이 유효하지 않다"))
                .andExpect(jsonPath("$.detail").value(containsString("승률")));
    }

    @Test
    void 시드가_빠지면_400() throws Exception {
        요청("equity-curve", SHORT_CURVE_REQUEST.replace("\"seed\": 20260821", "\"seed\": null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("시드")));
    }

    @Test
    void 총_거래_수가_상한을_넘으면_422() throws Exception {
        String 주_20회_501주 = SHORT_CURVE_REQUEST
                .replace("\"tradesPerWeek\": 1", "\"tradesPerWeek\": 20")
                .replace("\"weeks\": 4", "\"weeks\": 501");

        요청("equity-curve", 주_20회_501주)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("도메인 규칙 위반"))
                .andExpect(jsonPath("$.detail").value(containsString("총 거래 수")));
    }

    private static String 정규화(String json) {
        return json.replaceAll("\\s", "");
    }
}
