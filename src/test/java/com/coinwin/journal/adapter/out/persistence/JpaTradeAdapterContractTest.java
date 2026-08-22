package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.application.port.out.TradeRepositoryContract;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import com.coinwin.PostgresImage;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * JPA 어댑터가 <b>인메모리 어댑터와 같은 계약</b>을 통과하는지. 실제 PostgreSQL 을 띄운다.
 *
 * <p>H2 를 쓰지 않는 이유는 {@code .claude/docs/testing.md} 에 있다. 여기서는 특히 CHECK 제약과
 * {@code TIMESTAMPTZ} 와 {@code UUID} 타입이 걸려 있는데 셋 다 방언을 탄다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. 검사 대상은 어댑터·매퍼·마이그레이션이지 애플리케이션
 * 조립이 아니다. Flyway 를 직접 돌리므로 <b>V2 마이그레이션도 함께 검증된다</b> — 엔티티
 * 매핑과 실제 스키마가 어긋나면 여기서 깨진다.
 *
 * <p>저장 뒤 <b>영속성 컨텍스트를 비운다.</b> 비우지 않으면 조회가 1차 캐시에 남은 인스턴스를
 * 돌려주고, 그러면 매핑이 틀려도 왕복 테스트가 통과한다 — 검사하려던 것을 정확히 비껴간다.
 */
@Tag("integration")
class JpaTradeAdapterContractTest extends TradeRepositoryContract {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            PostgresImage.current());

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static EntityManager entityManager;

    private JpaTradeAdapter adapter;

    @BeforeAll
    static void 컨테이너를_띄우고_마이그레이션을_적용한다() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        entityManager = createEntityManager();
    }

    @BeforeEach
    void 표를_비우고_어댑터를_만든다() {
        entityManager.clear();
        jdbc.execute("TRUNCATE TABLE trade CASCADE");
        adapter = new JpaTradeAdapter(entityManager, new JPAQueryFactory(entityManager));
    }

    /**
     * {@code @Transactional} 은 Spring 프록시가 없으면 걸리지 않는다. 여기서는 트랜잭션을 직접
     * 열고 닫은 뒤 1차 캐시를 비워, 다음 조회가 반드시 DB 를 다시 읽게 한다.
     */
    @Override
    protected SaveTradePort savePort() {
        return trade -> {
            entityManager.getTransaction().begin();
            adapter.save(trade);
            entityManager.getTransaction().commit();
            entityManager.clear();
        };
    }

    @Override
    protected LoadTradesPort loadPort() {
        return adapter;
    }

    private static EntityManager createEntityManager() {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.coinwin.journal.adapter.out.persistence");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
        return factory.getObject().createEntityManager();
    }
}
