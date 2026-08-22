package com.coinwin;

import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트가 띄우는 PostgreSQL 이미지. <b>이름은 {@code libs.versions.toml} 한 곳에만 있다.</b>
 *
 * <p>여기에 기본값을 두지 않는 것이 요점이다. 예전에는 네 테스트가 각자
 * {@code System.getProperty("coinwin.postgres.image", "…")} 를 부르면서 <b>기본값을 손으로
 * 적어 두었고, 그 사본이 썩었다</b> — Phase 7 에서 이미지를 {@code pgvector/pgvector:pg18} 로
 * 바꿨는데 두 곳은 {@code postgres:18-alpine} 을 그대로 가리키고 있었다.
 *
 * <p>Gradle 은 언제나 이 프로퍼티를 넘기므로({@code build.gradle.kts} 의
 * {@code systemProperty}) 정상 경로에서는 아무 일도 없다. 문제는 IDE 에서 테스트를 직접
 * 돌릴 때인데, 그때 <b>조용히 다른 이미지로 도는 것보다 서서 이유를 말하는 편이 낫다</b> —
 * {@code compose.yaml} 이 경계한 "두 곳이 갈리면 로컬에서 통과한 SQL 이 통합 테스트에서
 * 방언 차이로 깨진다" 가 정확히 그 상황이다.
 */
public final class PostgresImage {

    private static final String PROPERTY = "coinwin.postgres.image";

    private PostgresImage() {
    }

    public static DockerImageName current() {
        String name = System.getProperty(PROPERTY);
        if (name == null) {
            throw new IllegalStateException(
                    PROPERTY + " 가 없다. 이미지 이름은 libs.versions.toml 에만 있고 Gradle 이 넘긴다 — "
                            + "`.\\gradlew.bat integrationTest` 로 돌린다. "
                            + "IDE 에서 돌리려면 VM 옵션에 -D" + PROPERTY + "=<이미지> 를 준다.");
        }
        // pgvector 이미지는 postgres 를 대신할 수 있다고 알려 줘야 Testcontainers 가 받아들인다.
        return DockerImageName.parse(name).asCompatibleSubstituteFor("postgres");
    }
}
