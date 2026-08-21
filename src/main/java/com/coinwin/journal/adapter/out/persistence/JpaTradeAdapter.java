package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래를 PostgreSQL 에 담는 어댑터.
 *
 * <p>캔들과 달리 JPA 를 쓰는 근거는 {@code docs/adr/016} 이다. 요지는 저장 대상이 스칼라 행
 * 하나가 아니라 <b>두 개의 순서 있는 목록을 거느린 덩어리</b>라는 것이다 — 분할 진입 계획과
 * 실제 체결. {@code JdbcTemplate} 으로는 세 테이블의 쓰기 순서와 고아 행 정리를 손으로 짜야 한다.
 *
 * <p>동적 조회만 QueryDSL 이다. 단건 조회는 {@code EntityManager.find} 가 더 짧고, 조건이
 * 없는 질의에 DSL 을 쓰면 얻는 것이 없다.
 */
@Repository
public class JpaTradeAdapter implements SaveTradePort, LoadTradesPort {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;

    public JpaTradeAdapter(EntityManager entityManager, JPAQueryFactory queryFactory) {
        this.entityManager = entityManager;
        this.queryFactory = queryFactory;
    }

    /**
     * {@inheritDoc}
     *
     * <p>같은 식별자의 행이 있으면 그 행을 덮어쓴다. {@code merge} 대신 {@code find} 후 필드를
     * 채우는 이유는 {@code @ElementCollection} 때문이다 — 관리되지 않는 인스턴스를 merge 하면
     * 자식 테이블이 전량 삭제 후 재삽입되는 경로가 조용히 늘어난다. 관리 중인 엔티티를 고치면
     * 더티 체킹이 바뀐 것만 본다.
     */
    @Override
    @Transactional
    public void save(Trade trade) {
        TradeEntity existing = entityManager.find(TradeEntity.class, trade.id().value());
        TradeEntity entity = TradeEntityWriter.write(trade, existing);
        if (existing == null) {
            entityManager.persist(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Trade> findById(TradeId id) {
        return Optional.ofNullable(entityManager.find(TradeEntity.class, id.value()))
                .map(TradeEntityReader::read);
    }

    /**
     * {@inheritDoc}
     *
     * <p>진입 시각 정렬은 <b>읽어 들인 뒤 자바에서</b> 한다. 진입 시각은 첫 체결의 시각이라
     * 자식 테이블에 있고, SQL 로 정렬하려면 그 값을 부모 테이블에 복사해 두어야 한다. 파생값의
     * 두 번째 사본은 언젠가 원본과 갈라진다. SQL 쪽 정렬({@code exit_at, id})은 그 자바 정렬이
     * 안정적으로 같은 답을 내게 하려는 것이다 — 정렬 키가 같은 두 거래의 순서까지 고정된다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ClosedTrade> findClosed(TradeQuery query) {
        return queryFactory.selectFrom(QTradeEntity.tradeEntity)
                .where(ClosedTradePredicates.of(query))
                .orderBy(QTradeEntity.tradeEntity.exitAt.asc(),
                        QTradeEntity.tradeEntity.id.asc())
                .fetch().stream()
                .map(TradeEntityReader::readClosed)
                .sorted(Comparator.comparing(ClosedTrade::openedAt))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Trade> findActive() {
        return queryFactory.selectFrom(QTradeEntity.tradeEntity)
                .where(QTradeEntity.tradeEntity.state.ne(TradeState.CLOSED))
                .orderBy(QTradeEntity.tradeEntity.plannedAt.asc(),
                        QTradeEntity.tradeEntity.id.asc())
                .fetch().stream()
                .map(TradeEntityReader::read)
                .toList();
    }
}
