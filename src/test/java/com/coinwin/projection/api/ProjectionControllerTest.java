package com.coinwin.projection.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.market.adapter.out.memory.InMemoryExchangeRateAdapter;
import com.coinwin.market.application.port.out.LoadExchangeRatePort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * HTTP 경계 검증. 계산은 도메인 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태 코드</b>,
 * 그리고 <b>같은 시드가 같은 응답을 낸다</b>는 약속만 본다.
 *
 * <p><b>환율만 고정한 어댑터로 갈아 끼운다.</b> 그대로 두면 이 테스트가 실제로 업비트를
 * 부르고, 그러면 두 가지가 무너진다 — 네트워크가 없으면 게이트가 실패하고, 환율이 매 순간
 * 달라져 <b>예제 응답 대조가 성립하지 않는다.</b>
 *
 * <p>컨텍스트는 그대로 띄운다. 손으로 조립하면 Boot 가 세운 Jackson 설정이 빠져
 * {@code Instant} 가 실제 응답과 <b>다른 모양</b>으로 직렬화되고, 그러면 이 테스트가
 * 증명하는 것이 진짜 와이어가 아니게 된다.
 */
@SpringBootTest
@Import(ProjectionControllerTest.FixedExchangeRate.class)
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

    /** 예제에 박힌 환율. 고정이라야 문서의 원화 금액이 매번 같다. */
    private static final Instant RATE_OBSERVED_AT = Instant.parse("2026-08-23T15:04:04Z");

    /** 업비트 대신 서는 어댑터. {@code @Primary} 라 진짜 어댑터가 있어도 이쪽이 이긴다. */
    @TestConfiguration
    static class FixedExchangeRate {

        @Bean
        @Primary
        LoadExchangeRatePort fixedExchangeRate() {
            return InMemoryExchangeRateAdapter.at("1370.00", RATE_OBSERVED_AT);
        }
    }

    @Autowired
    private WebApplicationContext context;

    private ResultActions 요청(String 경로, String body) throws Exception {
        return mockMvc().perform(post("/api/projections/" + 경로)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
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

    @Test
    void 목표_복리는_월별_자산과_거래당_요구치를_함께_돌려준다() throws Exception {
        요청("compound-target", ProjectionApiExamples.COMPOUND_REQUEST)
                .andExpect(status().isOk())
                // 첫 점은 거래 이전의 시작 자산이므로 점은 개월 수보다 하나 많다
                .andExpect(jsonPath("$.equity.length()").value(13))
                .andExpect(jsonPath("$.equity[0]").value(800.00))
                .andExpect(jsonPath("$.totalTrades").value(240))
                .andExpect(jsonPath("$.finalEquity").value(1436.69))
                // 목표는 순수익이므로 비용이 얼마든 도착점은 같다
                .andExpect(jsonPath("$.totalReturn").value(79.5856))
                .andExpect(jsonPath("$.priceMovePerTrade").value(0.2621))
                // 명목 = 800 × 20% × 10배 = 1,600. 전액을 넣는다는 전제가 아니다
                .andExpect(jsonPath("$.effectiveLeverage").value(2.00));
    }

    /** 문서의 예제와 실제 응답이 갈라지면 예제는 거짓말이 된다. 같은 요청, 같은 시드다. */
    @Test
    void 문서에_실린_예제_응답이_실제_응답과_일치한다() throws Exception {
        String 곡선 = 요청("equity-curve", ProjectionApiExamples.CURVE_REQUEST)
                .andReturn().getResponse().getContentAsString();
        String 분포 = 요청("monte-carlo", ProjectionApiExamples.MONTE_CARLO_REQUEST)
                .andReturn().getResponse().getContentAsString();
        String 목표 = 요청("compound-target", ProjectionApiExamples.COMPOUND_REQUEST)
                .andReturn().getResponse().getContentAsString();

        assertThat(정규화(곡선)).isEqualTo(정규화(ProjectionApiExamples.CURVE_RESPONSE));
        assertThat(정규화(분포)).isEqualTo(정규화(ProjectionApiExamples.MONTE_CARLO_RESPONSE));
        assertThat(정규화(목표)).isEqualTo(정규화(ProjectionApiExamples.COMPOUND_RESPONSE));
    }

    @Test
    void 원화는_업비트_환율로_옮긴_값이다() throws Exception {
        요청("compound-target", ProjectionApiExamples.COMPOUND_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won.wonPerUsdt").value(1370.00))
                // 1436.69 × 1370 = 1,968,265.3 → 원은 소수점이 없다
                .andExpect(jsonPath("$.won.finalEquity").value(1968265))
                .andExpect(jsonPath("$.won.totalProfit").value(872265));
    }

    @Test
    void 월_목표가_0_이면_400() throws Exception {
        요청("compound-target",
                ProjectionApiExamples.COMPOUND_REQUEST.replace("\"monthlyTarget\": 5",
                        "\"monthlyTarget\": 0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("월 목표 수익률")));
    }

    @Test
    void 목표_복리도_총_거래_수_상한을_넘으면_422() throws Exception {
        요청("compound-target",
                ProjectionApiExamples.COMPOUND_REQUEST.replace("\"tradesPerMonth\": 20",
                        "\"tradesPerMonth\": 1000"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("총 거래 수")));
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
