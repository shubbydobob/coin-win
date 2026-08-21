package com.coinwin.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 커밋된 {@code frontend/openapi.json} 을 만드는 <b>유일한</b> 경로.
 *
 * <p>다시 만드는 태스크({@code openApiSchema})와 낡았는지 보는 테스트가 이 클래스를 함께 쓴다.
 * 렌더링이 두 벌이면 게이트가 무작위로 깨지거나 반대로 영원히 통과한다.
 *
 * <p>DB 도 Docker 도 필요 없다. 테스트용 {@code application.yml} 이 Flyway 를 끄고 Hibernate
 * 방언을 직접 알려 주어 접속 0회로 컨텍스트가 뜨기 때문이다(Phase 3·5). 그래서 이 검사는
 * 기본 {@code test} 에서 돈다.
 *
 * <p>근거: {@code docs/spec/phase8-frontend.md} § 5.2
 */
final class OpenApiDocument {

    /** 생성물이지만 커밋한다. 아니면 프론트를 빌드할 때마다 백엔드를 띄워야 한다. */
    static final Path COMMITTED = Path.of("frontend", "openapi.json");

    static final String REGENERATE_COMMAND = ".\\gradlew.bat openApiSchema";

    private static final DefaultPrettyPrinter PRINTER = prettyPrinter();

    private OpenApiDocument() {
    }

    /**
     * 현재 코드가 내는 문서를 커밋 파일과 같은 형식으로 렌더링한다.
     *
     * <p>들여쓰기를 주는 이유는 diff 때문이다. 한 줄짜리 JSON 은 필드 하나가 바뀌어도
     * 파일 전체가 바뀐 것으로 보이고, 그러면 재생성을 리뷰할 수 없다.
     */
    static String render(WebApplicationContext context) throws Exception {
        String body = MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return format(new ObjectMapper().readTree(body));
    }

    /**
     * 이미 읽어 둔 문서를 같은 형식으로 렌더링한다.
     *
     * <p>낡음 검사의 위반 픽스처가 쓴다 — 픽스처가 다른 형식으로 나오면 "필드가 지워져서"
     * 가 아니라 "줄바꿈이 달라서" 잡히게 되고, 그러면 그 테스트는 아무것도 증명하지 않는다.
     */
    static String format(JsonNode document) throws Exception {
        return new ObjectMapper().writer(PRINTER).writeValueAsString(document) + "\n";
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                // 기본값은 `"키" : 값` 이다. 콜론 앞 공백을 없애 흔한 JSON 모양으로 맞춘다.
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }
}
