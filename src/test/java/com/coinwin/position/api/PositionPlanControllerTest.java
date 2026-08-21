package com.coinwin.position.api;

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
 * HTTP 경계 검증. 계산 자체는 도메인 테스트가 이미 증명했으므로 여기서는
 * <b>매핑과 상태 코드</b>만 본다.
 *
 * <p>소수 값은 응답 본문 문자열로 확인한다. 스케일이 그대로 실리는지(16 이 아니라 16.00 인지)가
 * 이 계층에서 깨질 수 있는 지점이기 때문이다.
 */
@SpringBootTest
class PositionPlanControllerTest {

    private static final String LONG_50_SPLIT = """
            {
              "direction": "LONG",
              "entries": [
                { "price": 60000, "allocation": 50 },
                { "price": 58000, "allocation": 50 }
              ],
              "stopLoss": 56000,
              "takeProfit": 66000,
              "leverage": 10,
              "accountBalance": 800,
              "riskPercent": 2
            }""";

    @Autowired
    private WebApplicationContext context;

    private ResultActions 분석요청(String body) throws Exception {
        return MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/position-plans/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void 분할_계획을_보내면_체결_상태별_리스크가_돌아온다() throws Exception {
        String 응답 = 분석요청(LONG_50_SPLIT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fillStates.length()").value(2))
                .andExpect(jsonPath("$.fillStates[0].filledEntries").value(1))
                .andExpect(jsonPath("$.fillStates[1].filledEntries").value(2))
                .andExpect(jsonPath("$.weakRiskReward").value(false))
                .andExpect(jsonPath("$.marginExceedsBalance").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(응답)
                .contains("\"averageEntryPrice\":60000.00")
                .contains("\"averageEntryPrice\":59000.00")
                .contains("\"liquidationPrice\":54216.87")
                .contains("\"liquidationPrice\":53313.25")
                .contains("\"maxLoss\":10.67")
                .contains("\"maxLoss\":16.00")
                .contains("\"requiredMargin\":31.47")
                .contains("\"riskRewardRatio\":2.33");
    }

    @Test
    void 값_자체가_부적절하면_400() throws Exception {
        String 음수_진입가 = LONG_50_SPLIT.replace("\"price\": 60000", "\"price\": -60000");

        분석요청(음수_진입가)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("값이 유효하지 않다"))
                .andExpect(jsonPath("$.detail").value(containsString("음수")));
    }

    @Test
    void 레버리지가_빠지면_400() throws Exception {
        String 레버리지_없음 = LONG_50_SPLIT.replace("\"leverage\": 10,", "");

        분석요청(레버리지_없음)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("레버리지")));
    }

    @Test
    void 손절가_방향이_틀리면_422() throws Exception {
        String 손절가가_진입가_위 = LONG_50_SPLIT.replace("\"stopLoss\": 56000", "\"stopLoss\": 58500");

        분석요청(손절가가_진입가_위)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("도메인 규칙 위반"))
                .andExpect(jsonPath("$.detail").value(containsString("최저 진입가")));
    }

    @Test
    void 비중_합이_100이_아니면_422() throws Exception {
        String 합이_90 = LONG_50_SPLIT.replace(
                "\"price\": 58000, \"allocation\": 50",
                "\"price\": 58000, \"allocation\": 40");

        분석요청(합이_90)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("100")));
    }

    @Test
    void 손절가가_청산가_너머면_422() throws Exception {
        String 레버리지_3배_먼_손절 = LONG_50_SPLIT
                .replace("\"stopLoss\": 56000", "\"stopLoss\": 40000")
                .replace("\"leverage\": 10", "\"leverage\": 3");

        분석요청(레버리지_3배_먼_손절)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("손절")));
    }
}
