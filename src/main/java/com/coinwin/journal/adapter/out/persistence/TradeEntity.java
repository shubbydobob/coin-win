package com.coinwin.journal.adapter.out.persistence;

import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.position.domain.Direction;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code trade} 한 행. <b>도메인의 {@code Trade} 와 별개 클래스다</b> — ArchUnit 규칙 1 이
 * 도메인에 {@code @Entity} 를 다는 것을 금지하기 때문이고, 금지하는 이유는 저장 형식이
 * 도메인 모양을 끌고 가지 못하게 하기 위해서다. 실제로 여기서 세 타입이 한 테이블로 뭉개진다.
 *
 * <p><b>게터가 없다.</b> 필드 접근 방식({@code @Id} 가 필드에 있다)이고 읽는 쪽은 같은 패키지의
 * {@link TradeEntityMapper} 하나뿐이다. 게터 스무 개는 이 클래스를 두 배로 만들면서 아무것도
 * 캡슐화하지 않는다 — 상태를 지키는 일은 도메인이 이미 하고 있다.
 *
 * <p>상태에 따라 비는 칸(진입 맥락·청산)은 {@code V2__trade.sql} 의 CHECK 제약이 지킨다.
 */
@Entity
@Table(name = "trade")
class TradeEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    TradeState state;

    @Enumerated(EnumType.STRING)
    Direction direction;

    int leverage;

    @Column(name = "stop_loss")
    BigDecimal stopLoss;

    @Column(name = "take_profit")
    BigDecimal takeProfit;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "planned_at")
    Instant plannedAt;

    @ElementCollection
    @CollectionTable(name = "trade_planned_entry", joinColumns = @JoinColumn(name = "trade_id"))
    @OrderColumn(name = "seq")
    List<PlannedEntryRow> plannedEntries = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "trade_fill", joinColumns = @JoinColumn(name = "trade_id"))
    @OrderColumn(name = "seq")
    List<FillRow> fills = new ArrayList<>();

    @Column(name = "price_at_entry")
    BigDecimal priceAtEntry;

    @Enumerated(EnumType.STRING)
    @Column(name = "ichimoku_position")
    BandPosition ichimokuPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "bollinger_position")
    BandPosition bollingerPosition;

    String rationale;

    @Column(name = "exit_price")
    BigDecimal exitPrice;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    @Column(name = "exit_at")
    Instant exitAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_reason")
    ExitReason exitReason;

    BigDecimal fees;

    BigDecimal funding;

    protected TradeEntity() {
    }
}
