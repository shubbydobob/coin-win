package com.coinwin.journal.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code trade_fill} 한 행.
 *
 * <p>{@code TIMESTAMP_UTC} 를 명시하는 이유는 {@code JdbcCandleAdapter} 가 {@code Timestamp}
 * 대신 {@code OffsetDateTime} 을 쓰는 이유와 같다 — 시간대를 잃으면 값이 조용히 밀린다.
 */
@Embeddable
class FillRow {

    BigDecimal price;

    BigDecimal quantity;

    @Column(name = "filled_at")
    @JdbcTypeCode(SqlTypes.TIMESTAMP_UTC)
    Instant filledAt;

    protected FillRow() {
    }

    FillRow(BigDecimal price, BigDecimal quantity, Instant filledAt) {
        this.price = price;
        this.quantity = quantity;
        this.filledAt = filledAt;
    }
}
