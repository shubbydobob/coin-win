package com.coinwin.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 오류 응답의 계약을 <b>실제 응답과 스키마 양쪽에서</b> 못 박는다.
 *
 * <p>{@code docs/spec/phase8-frontend.md} § 7 은 "백엔드는 RFC 7807 {@code ProblemDetail} 을
 * 낸다" 를 전제로 화면 처리를 정한다. 그런데 그 전제를 검사하는 것이 아무것도 없었고,
 * <b>스키마는 오히려 반대로 말하고 있었다</b> — springdoc 이 오류 응답에 핸들러의 반환 타입을
 * 씌워서, 계획 저장이 422 일 때 {@code TradeResponse} 가 온다고 적혀 있었다.
 *
 * <p>그래서 여기서 보는 것이 셋이다.
 *
 * <ol>
 *   <li>진짜 오류 응답의 media type 이 스키마가 말하는 것과 같은가</li>
 *   <li>진짜 오류 본문의 키와 스키마의 프로퍼티가 <b>양방향으로</b> 맞는가 —
 *       손으로 적은 필드 목록이 실제와 갈라지는 것을 막는 자리다</li>
 *   <li>2xx 가 아닌 응답 중 성공 DTO 를 가리키는 것이 하나도 남아 있지 않은가</li>
 * </ol>
 *
 * <p>세 번째가 없으면 커스터마이저가 조용히 아무것도 안 하게 돼도 알 수 없다.
 */
@SpringBootTest
class ProblemDetailContractTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    private static final String PLAN = """
            {
              "direction": "LONG",
              "entries": [ { "price": 60000, "allocation": 100 } ],
              "stopLoss": 56000,
              "takeProfit": 66000,
              "leverage": 10,
              "accountBalance": 800,
              "riskPercent": 2
            }""";

    /** 값 자체가 부적절하다 — 400. */
    private static final String NEGATIVE_PRICE = PLAN.replace("\"price\": 60000", "\"price\": -60000");

    /** 값은 멀쩡한데 계획으로 성립하지 않는다 — 422. */
    private static final String STOP_ABOVE_ENTRY = PLAN.replace("\"stopLoss\": 56000", "\"stopLoss\": 61000");

    @Autowired
    private WebApplicationContext context;

    @Test
    void 오류_본문은_스키마가_말하는_media_type_으로_온다() throws Exception {
        for (String body : List.of(NEGATIVE_PRICE, STOP_ABOVE_ENTRY)) {
            assertThat(analysisFailure(body).getContentType())
                    .describedAs("오류 본문의 media type")
                    .startsWith(PROBLEM_JSON);
        }
    }

    @Test
    void 오류_본문의_키는_전부_스키마에_선언돼_있다() throws Exception {
        List<String> declared = names(problemDetailSchema().path("properties"));

        for (String body : List.of(NEGATIVE_PRICE, STOP_ABOVE_ENTRY)) {
            assertThat(names(errorBody(body)))
                    .describedAs("실제 오류 본문의 키 — 스키마에 없는 것이 있으면 소비자가 모른다")
                    .isSubsetOf(declared);
        }
    }

    @Test
    void required_로_적은_키는_실제_응답에_언제나_있다() throws Exception {
        List<String> required = texts(problemDetailSchema().path("required"));

        assertThat(required).contains("detail");
        assertThat(required)
                .describedAs("about:blank 인 type 은 직렬화에서 빠진다. RFC 가 아니라 응답이 정한다")
                .doesNotContain("type");
        for (String body : List.of(NEGATIVE_PRICE, STOP_ABOVE_ENTRY)) {
            assertThat(names(errorBody(body)))
                    .describedAs("required 가 실제보다 넓으면 타입이 없는 값을 있다고 단언한다")
                    .containsAll(required);
        }
    }

    @Test
    void 오류_응답_중_성공_DTO_를_가리키는_것은_하나도_없다() throws Exception {
        JsonNode paths = document().path("paths");

        List<String> wrong = new ArrayList<>();
        for (String path : names(paths)) {
            for (String method : names(paths.path(path))) {
                JsonNode responses = paths.path(path).path(method).path("responses");
                names(responses).stream()
                        .filter(code -> !code.startsWith("2"))
                        .filter(code -> !pointsAtProblemDetail(responses.path(code)))
                        .map(code -> method + " " + path + " → " + code)
                        .forEach(wrong::add);
            }
        }

        assertThat(wrong)
                .describedAs("오지 않는 본문을 선언한 오류 응답")
                .isEmpty();
        assertThat(problemDetailSchema().isMissingNode()).isFalse();
    }

    private static boolean pointsAtProblemDetail(JsonNode response) {
        JsonNode content = response.path("content");
        return names(content).equals(List.of(PROBLEM_JSON))
                && PROBLEM_REF.equals(content.path(PROBLEM_JSON).path("schema").path("$ref").asText(""));
    }

    private MockHttpServletResponse analysisFailure(String body) throws Exception {
        return mockMvc().perform(post("/api/position-plans/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse();
    }

    private JsonNode errorBody(String body) throws Exception {
        return new ObjectMapper().readTree(analysisFailure(body).getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode problemDetailSchema() throws Exception {
        return document().path("components").path("schemas").path("ProblemDetail");
    }

    private JsonNode document() throws Exception {
        String body = mockMvc().perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static List<String> names(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
