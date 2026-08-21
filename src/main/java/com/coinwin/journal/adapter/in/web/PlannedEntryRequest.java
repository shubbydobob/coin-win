package com.coinwin.journal.adapter.in.web;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.PlannedEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 분할 진입 계획 한 회차. */
@Schema(description = "분할 진입 한 회차. 비중의 합은 정확히 100 이어야 한다")
public record PlannedEntryRequest(

        @Schema(description = "이 회차의 지정가", example = "60000")
        BigDecimal price,

        @Schema(description = "이 회차에 넣을 비중. 50 은 50% 를 뜻한다", example = "50")
        BigDecimal allocation) {

    PlannedEntry toEntry() {
        return new PlannedEntry(Price.of(price), Percentage.of(allocation));
    }
}
