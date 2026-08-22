package com.coinwin.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

/**
 * 커밋된 {@code frontend/openapi.json} 이 현재 자바 코드와 같은지 본다.
 *
 * <p>이 게이트가 없으면 생성물을 커밋한 대가로 "손으로 쓴 사본" 과 똑같은 문제를 얻는다 —
 * 백엔드 DTO 를 고치고 재생성을 잊으면 <b>타입은 여전히 컴파일되고 런타임에만 틀린다.</b>
 *
 * <p>둘째 테스트가 이 게이트가 실제로 발동하는지를 증명한다. 소스를 임시로 깨뜨렸다 되돌리는
 * 대신 <b>위반 픽스처</b>를 쓴다 — 현재 문서에서 필드 하나를 지운 것을 "재생성을 잊은 커밋
 * 파일" 로 세우고, 검사가 그것을 잡는지 본다. 임시 파괴는 확인이 1회성이고 원복 실패 위험만
 * 더하지만, 픽스처는 매 빌드 재검증된다. 근거는 {@code .claude/docs/roadmap.md} Phase 0 과
 * 같다.
 */
@SpringBootTest
class OpenApiSchemaFreshnessTest {

    /** 지워도 문서가 여전히 유효한 JSON 인 필드. 어느 것이든 상관없다. */
    private static final String VICTIM_SCHEMA = "SummaryResponse";

    private static final String VICTIM_FIELD = "netPnl";

    @Autowired
    private WebApplicationContext context;

    @Test
    void 커밋된_스키마가_현재_코드와_같다() throws Exception {
        String committed = Files.readString(OpenApiDocument.COMMITTED, StandardCharsets.UTF_8);

        assertUpToDate(OpenApiDocument.render(context), committed);
    }

    @Test
    void 필드_하나가_어긋난_스키마는_낡음으로_잡힌다() throws Exception {
        String rendered = OpenApiDocument.render(context);
        String stale = withoutOneField(rendered);

        assertThatThrownBy(() -> assertUpToDate(rendered, stale))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(OpenApiDocument.REGENERATE_COMMAND);
    }

    /**
     * 낡음 판정 그 자체. 두 테스트가 같은 함수를 부르므로, 통과하는 쪽이 보는 규칙과
     * 발동을 증명하는 쪽이 보는 규칙이 갈라질 수 없다.
     */
    private static void assertUpToDate(String rendered, String committed) {
        assertThat(committed)
                .describedAs("커밋된 %s 가 현재 코드와 다르다. `%s` 로 다시 만들고 함께 커밋한다",
                        OpenApiDocument.COMMITTED, OpenApiDocument.REGENERATE_COMMAND)
                .isEqualTo(rendered);
    }

    private static String withoutOneField(String document) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(document);
        ObjectNode properties = (ObjectNode) root
                .path("components").path("schemas").path(VICTIM_SCHEMA).path("properties");

        assertThat(properties.has(VICTIM_FIELD))
                .describedAs("픽스처가 지우려는 %s.%s 가 문서에 없다. 이름이 바뀌었는가",
                        VICTIM_SCHEMA, VICTIM_FIELD)
                .isTrue();
        properties.remove(VICTIM_FIELD);

        return OpenApiDocument.format(root);
    }
}
