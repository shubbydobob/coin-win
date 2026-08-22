package com.coinwin.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 시크릿이 저장소에 들어오는 것을 막는다.
 *
 * <p>Phase 8 이 남긴 문장이 이 테스트의 이유다 — <b>"소스 트리에 들어오면 .gitignore 로 가려야
 * 하고, 가린 것은 언젠가 실수로 커밋된다."</b> 그때는 빌드 산출물 이야기였고 여기서는 키다.
 * 대가가 훨씬 크다.
 *
 * <p>{@code .gitignore} 한 줄이 지워져도 아무 일도 일어나지 않는다는 것이 문제다. 다음 커밋에
 * 키가 딸려 들어가고, 그 사실은 밀어 올린 뒤에야 드러난다. <b>사람의 기억이 아니라 테스트가
 * 막는다.</b>
 *
 * <p>진짜 값을 검사하지 않는다. {@code .env} 가 있는지조차 보지 않고 <b>규칙이 서 있는지만</b>
 * 본다 — 키가 있는 기계에서만 도는 테스트는 CI 에서 아무것도 증명하지 않는다.
 */
class SecretsAreNotCommittedTest {

    /** 지워지면 안 되는 무시 규칙. 각각 실제로 시크릿이 들어갈 수 있는 경로다. */
    private static final List<String> REQUIRED_IGNORES =
            List.of(".env", "*.key", "application-secret*.yml");

    private static final Path ROOT = Path.of("");

    @Test
    void gitignore_가_시크릿_경로를_계속_막는다() throws IOException {
        List<String> lines = ignoreLines();

        assertThat(lines)
                .describedAs(".gitignore 에서 사라지면 다음 커밋에 시크릿이 딸려 들어간다")
                .containsAll(REQUIRED_IGNORES);
    }

    /**
     * 예시 파일은 커밋된다. 값이 비어 있어야 한다 — 여기에 진짜 키를 적어 두면 무시 규칙이
     * 아무 일도 하지 않는다. {@code .env} 를 막아 놓고 {@code .env.example} 로 새는 것이
     * 가장 흔한 사고다.
     */
    @Test
    void 예시_파일에는_값이_비어_있다() throws IOException {
        Path example = ROOT.resolve(".env.example");
        assertThat(example).exists();

        List<String> assignments = Files.readAllLines(example, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        assertThat(assignments)
                .describedAs("예시 파일에 실제 키를 적으면 .gitignore 가 무의미해진다")
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).endsWith("="));
    }

    /**
     * {@code .env} 는 <b>저장소 루트</b>에 둔다. {@code src/main/resources} 아래로 옮기면
     * {@code bootJar} 안에 그대로 실려 나간다 — 배포물에 키가 박히는 것이고, 그때는
     * {@code .gitignore} 가 막을 수 있는 문제가 아니다.
     */
    @Test
    void 리소스_디렉터리에는_시크릿_파일이_없다() throws IOException {
        Path resources = ROOT.resolve("src/main/resources");

        try (var files = Files.walk(resources)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .describedAs("리소스에 들어가면 bootJar 에 실려 나간다")
                    .noneMatch(name -> name.equals(".env")
                            || name.endsWith(".key")
                            || name.startsWith("application-secret"));
        }
    }

    private static List<String> ignoreLines() throws IOException {
        return Files.readAllLines(ROOT.resolve(".gitignore"), StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .toList();
    }
}
