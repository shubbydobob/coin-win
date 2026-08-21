package com.coinwin.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 응답 스키마가 <b>사실대로</b> 말하는지 검사한다.
 *
 * <p>springdoc 은 {@code required} 를 내지 않는다. 그대로 두면 생성된 타입에서 모든 필드가
 * optional 이 되고, 그러면 두 가지가 한꺼번에 무너진다 — 항상 있는 값에까지 가드를 달게 되고
 * (잡음), <b>진짜 null 인 것과 항상 있는 것이 구별되지 않는다</b>(위험).
 *
 * <p>고치는 방법은 둘 다 필요하다. {@code required} 만 세우고 {@code nullable} 을 빠뜨리면
 * optional 이던 시절보다 나쁘다 — 타입이 "언제나 있다" 고 단언하는데 실제로는 null 이 오므로
 * 컴파일러가 가드를 <b>지우게</b> 만든다.
 *
 * <p>그래서 이 테스트는 양쪽을 함께 못 박는다. 특히 마지막 테스트는 null 이 오는 필드가
 * <b>정확히 셋</b>이라는 것을 전수로 센다 — 넷째가 생기면 목록을 고치지 않고는 통과할 수 없다.
 *
 * <p>근거: {@code docs/spec/phase8-frontend.md} § 5.4
 */
@SpringBootTest
class ResponseSchemaContractTest {

    private static final String RESPONSE_SUFFIX = "Response";

    /** 응답 필드 중 실제로 null 이 오는 것 전부. 전수로 셌다. */
    private static final Map<String, List<String>> NULLABLE_FIELDS = Map.of(
            "SummaryResponse", List.of("profitFactor"),
            "TradeResponse", List.of("entry", "outcome"));

    @Autowired
    private WebApplicationContext context;

    @Test
    void 응답_스키마는_모든_필드가_required_다() throws Exception {
        JsonNode schemas = schemas();

        List<String> incomplete = new ArrayList<>();
        names(schemas).stream()
                .filter(name -> name.endsWith(RESPONSE_SUFFIX))
                .filter(name -> !required(schemas.get(name)).containsAll(properties(schemas.get(name))))
                .forEach(incomplete::add);

        assertThat(incomplete)
                .describedAs("required 가 빠진 응답 스키마 — 생성된 타입에서 optional 이 된다")
                .isEmpty();
    }

    @Test
    void 요청_스키마에는_required_를_붙이지_않는다() throws Exception {
        JsonNode schemas = schemas();

        // 요청은 실제로 선택 필드가 있고, 검증은 어차피 서버가 한다.
        assertThat(required(schemas.get("RunBacktestRequest"))).doesNotContain("costs");
        assertThat(required(schemas.get("JournalQueryRequest"))).doesNotContain("topK");
        assertThat(required(schemas.get("TradeQueryParams"))).isEmpty();
    }

    @Test
    void null_이_오는_응답_필드는_정확히_셋뿐이다() throws Exception {
        JsonNode schemas = schemas();

        List<String> actual = new ArrayList<>();
        for (String name : names(schemas)) {
            if (!name.endsWith(RESPONSE_SUFFIX)) {
                continue;
            }
            JsonNode props = schemas.get(name).path("properties");
            names(props).stream()
                    .filter(field -> acceptsNull(props.get(field)))
                    .map(field -> name + "." + field)
                    .forEach(actual::add);
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedNullableFields());
    }

    private static List<String> expectedNullableFields() {
        return NULLABLE_FIELDS.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(field -> entry.getKey() + "." + field))
                .toList();
    }

    /** 3.1 은 {@code type: [T, "null"]} 로 내지만 {@code $ref} 필드는 합성 스키마로 나온다. */
    private static boolean acceptsNull(JsonNode property) {
        return hasNullType(property.path("type"))
                || anyAcceptsNull(property.path("anyOf"))
                || anyAcceptsNull(property.path("oneOf"));
    }

    private static boolean hasNullType(JsonNode type) {
        if (type.isArray()) {
            return stream(type).anyMatch(entry -> "null".equals(entry.asText()));
        }
        return "null".equals(type.asText(""));
    }

    private static boolean anyAcceptsNull(JsonNode alternatives) {
        return stream(alternatives).anyMatch(ResponseSchemaContractTest::acceptsNull);
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static List<String> names(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> properties(JsonNode schema) {
        return names(schema.path("properties"));
    }

    private static List<String> required(JsonNode schema) {
        return stream(schema.path("required")).map(JsonNode::asText).toList();
    }

    private JsonNode schemas() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(body).path("components").path("schemas");
    }
}
