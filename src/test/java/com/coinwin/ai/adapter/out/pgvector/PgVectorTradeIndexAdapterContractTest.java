package com.coinwin.ai.adapter.out.pgvector;

import com.coinwin.ai.DeterministicEmbeddingModel;
import com.coinwin.ai.application.port.out.IndexTradesPort;
import com.coinwin.ai.application.port.out.SearchTradesPort;
import com.coinwin.ai.application.port.out.TradeIndexContract;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.coinwin.PostgresImage;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * pgvector 어댑터가 <b>인메모리 어댑터와 같은 계약</b>을 통과하는지. 실제 PostgreSQL 을 띄운다.
 *
 * <p>임베딩은 {@link DeterministicEmbeddingModel} 이다 — <b>OPENAI_API_KEY 없이 돈다.</b>
 * 그래도 검증되는 것은 적지 않다. V3 마이그레이션, {@code vector(1536)} 차원 일치, 식별자로
 * 덮어쓰기, 비우기, topK 가 전부 진짜 DB 위에서 확인된다. 검증되지 않는 것은 임베딩의 품질
 * 하나뿐이고 그것은 어떤 자동 테스트로도 고정할 수 없다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는 이유는 {@code JpaTradeAdapterContractTest} 와 같다 —
 * 검사 대상은 어댑터와 마이그레이션이지 애플리케이션 조립이 아니다.
 */
@Tag("integration")
class PgVectorTradeIndexAdapterContractTest extends TradeIndexContract {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            PostgresImage.current());

    private static JdbcTemplate jdbc;

    private PgVectorTradeIndexAdapter adapter;

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
    void 표를_비우고_어댑터를_만든다() {
        jdbc.execute("TRUNCATE TABLE vector_store");
        PgVectorStore store = PgVectorStore.builder(jdbc, new DeterministicEmbeddingModel())
                .dimensions(DeterministicEmbeddingModel.DIMENSIONS)
                .initializeSchema(false)
                .build();
        store.afterPropertiesSet();
        adapter = new PgVectorTradeIndexAdapter(store, JdbcClient.create(jdbc));
    }

    @Override
    protected IndexTradesPort indexPort() {
        return adapter;
    }

    @Override
    protected SearchTradesPort searchPort() {
        return adapter;
    }
}
