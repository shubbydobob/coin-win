package com.coinwin.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 질의 파라미터가 <b>와이어에 실리는 모양 그대로</b> 문서화되는지 본다.
 *
 * <p>스프링은 {@code TradeQueryParams} 같은 레코드를 <b>개별 질의 파라미터</b>로 바인딩한다 —
 * {@code ?closedFrom=…&direction=LONG}. 그런데 springdoc 은 애너테이션이 없으면 그것을
 * <b>{@code params} 라는 이름의 객체 파라미터 하나</b>로 적는다. 그 문서를 그대로 믿은 소비자는
 * 있지도 않은 {@code ?params=…} 를 보내게 된다.
 *
 * <p>§ 5.4 · § 7 과 같은 종류이고, 이번에는 요청 쪽이다. 고치는 것은 {@code @ParameterObject}
 * 한 줄이지만 그것이 빠지는 것은 사람의 기억이 아니라 이 테스트가 막는다.
 *
 * <p>규칙은 하나다 — <b>질의 파라미터의 스키마는 객체를 가리키지 않는다.</b> 질의 문자열에는
 * 중첩이 없으므로 이 규칙에 예외가 있을 수 없다.
 */
@SpringBootTest
class QueryParameterContractTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void 질의_파라미터는_객체를_가리키지_않는다() throws Exception {
        JsonNode paths = document().path("paths");

        List<String> nested = new ArrayList<>();
        for (String path : names(paths)) {
            for (String method : names(paths.path(path))) {
                paths.path(path).path(method).path("parameters").forEach(parameter -> {
                    if (isNestedQuery(parameter)) {
                        nested.add(method + " " + path + " → " + parameter.path("name").asText());
                    }
                });
            }
        }

        assertThat(nested)
                .describedAs("객체로 문서화된 질의 파라미터 — 와이어에는 그런 모양이 없다")
                .isEmpty();
    }

    @Test
    void 거래_조회_조건은_개별_파라미터로_나온다() throws Exception {
        JsonNode parameters = document().path("paths").path("/api/trades").path("get").path("parameters");

        List<String> declared = new ArrayList<>();
        parameters.forEach(parameter -> declared.add(parameter.path("name").asText()));

        assertThat(declared)
                .containsExactlyInAnyOrder("closedFrom", "closedTo", "direction", "exitReason", "followedPlan");
    }

    private static boolean isNestedQuery(JsonNode parameter) {
        return "query".equals(parameter.path("in").asText())
                && !parameter.path("schema").path("$ref").isMissingNode();
    }

    private JsonNode document() throws Exception {
        String body = MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body);
    }

    private static List<String> names(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
