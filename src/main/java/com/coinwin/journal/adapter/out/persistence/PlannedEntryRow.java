package com.coinwin.journal.adapter.out.persistence;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/** {@code trade_planned_entry} 한 행. 순서는 {@code @OrderColumn(seq)} 이 갖는다. */
@Embeddable
class PlannedEntryRow {

    BigDecimal price;

    BigDecimal allocation;

    protected PlannedEntryRow() {
    }

    PlannedEntryRow(BigDecimal price, BigDecimal allocation) {
        this.price = price;
        this.allocation = allocation;
    }
}
