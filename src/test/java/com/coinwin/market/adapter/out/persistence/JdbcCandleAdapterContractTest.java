package com.coinwin.market.adapter.out.persistence;

import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPortContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 영속화 어댑터가 <b>메모리 어댑터와 같은 계약</b>을 통과하는지. 실제 PostgreSQL 을 띄운다.
 *
 * <p>H2 를 쓰지 않는 이유는 {@code .claude/docs/testing.md} 에 있다 — 방언 차이로 거짓
 * 통과가 난다. 이 어댑터는 {@code ON CONFLICT} 와 {@code TIMESTAMPTZ} 에 의존하는데 둘 다
 * PostgreSQL 것이다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 검사 대상은 어댑터와 마이그레이션이지 애플리케이션
 * 조립이 아니다. Flyway 를 직접 돌리므로 <b>V1 마이그레이션도 함께 검증된다.</b>
 */
@Tag("integration")
class JdbcCandleAdapterContractTest extends SaveCandlesPortContract {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(System.getProperty("coinwin.postgres.image", "postgres:18-alpine")));

    private static JdbcTemplate jdbc;

    private JdbcCandleAdapter adapter;

    @BeforeAll
    static void 컨테이너를_띄우고_마이그레이션을_적용한다() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void 테이블을_비운다() {
        jdbc.execute("TRUNCATE TABLE candle");
        adapter = new JdbcCandleAdapter(jdbc);
    }

    @Override
    protected LoadCandlesPort loadPort() {
        return adapter;
    }

    @Override
    protected SaveCandlesPort savePort() {
        return adapter;
    }
}
