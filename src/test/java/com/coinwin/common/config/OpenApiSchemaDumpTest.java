package com.coinwin.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code frontend/openapi.json} 을 현재 코드에서 다시 만든다. {@code .\gradlew.bat openApiSchema}.
 *
 * <p><b>기본 {@code test} 에서 걷어낸 이유가 이 클래스의 요점이다.</b> 여기서 함께 돌면 매번
 * 파일을 덮어쓰게 되고, 그러면 {@link OpenApiSchemaFreshnessTest} 는 자기가 방금 쓴 것을 읽고
 * 언제나 통과한다 — 게이트가 있는 것처럼 보이면서 아무것도 지키지 않는다.
 *
 * <p>{@code crossCheck} · {@code liveAi} 와 같은 형태다. 사람이 부르는 태스크로 분리한다.
 */
@Tag("schema")
@SpringBootTest
class OpenApiSchemaDumpTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void 현재_코드의_스키마를_커밋_파일에_쓴다() throws Exception {
        String rendered = OpenApiDocument.render(context);

        Files.writeString(OpenApiDocument.COMMITTED, rendered, StandardCharsets.UTF_8);

        System.out.println("작성: " + OpenApiDocument.COMMITTED.toAbsolutePath()
                + " (" + rendered.getBytes(StandardCharsets.UTF_8).length + " 바이트)");
    }
}
