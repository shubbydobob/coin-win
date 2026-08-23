package com.coinwin.journal.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.application.port.out.TradeRepositoryContract;
import com.coinwin.journal.domain.Fill;
import com.coinwin.journal.domain.OpenTrade;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

    /**
     * 저장된 순번은 <b>1 부터다.</b>
     *
     * <p>JPA {@code @OrderColumn} 은 리스트 인덱스를 그대로 적으므로 기본값이 0 이다. 화면은
     * "1회차 진입가" 라고 부르는데 표에는 {@code seq = 0} 이 들어 있었고, 그러면 SQL 을 직접
     * 읽는 사람이 매번 한 칸을 옮겨 세야 한다. {@code @ListIndexBase(1)} 이 그 어긋남을 없앤다 —
     * 자바 리스트는 그대로 0 부터이고 저장만 1 부터다.
     *
     * <p><b>이 사실은 계약 테스트가 잡지 못한다.</b> 포트 계약은 "넣은 순서대로 나오는가" 만
     * 보고, 그것은 0 부터여도 1 부터여도 똑같이 통과한다. 컬럼을 직접 읽는 수밖에 없다.
     */
    @Test
    void 저장된_순번은_0_이_아니라_1_부터다() {
        savePort().save(JournalFixtures.open());

        assertThat(jdbc.queryForList("SELECT seq FROM trade_planned_entry ORDER BY seq", Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbc.queryForList("SELECT seq FROM trade_fill ORDER BY seq", Integer.class))
                .containsExactly(1, 2);
    }

    /**
     * 그리고 <b>1 부터 적은 것이 순서 그대로 읽힌다.</b> 저장만 옮기고 읽기가 따라오지 않으면
     * 분할 진입의 순서가 뒤집히고, 그 순서가 곧 평단의 변천사다.
     */
    @Test
    void 순번을_옮겨도_체결_순서는_그대로다() {
        OpenTrade saved = JournalFixtures.open();
        savePort().save(saved);

        OpenTrade loaded = (OpenTrade) loadPort().findById(saved.id()).orElseThrow();

        assertThat(loaded.entries().fills())
                .extracting(Fill::at)
                .containsExactly(JournalFixtures.FIRST_FILL_AT, JournalFixtures.SECOND_FILL_AT);
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
