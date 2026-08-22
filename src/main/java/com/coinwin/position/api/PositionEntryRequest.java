package com.coinwin.position.api;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.PlannedEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 분할 진입 한 건. 순서가 체결 순서다. */
@Schema(description = "분할 진입 한 회차. 배열의 순서가 체결될 순서다")
public record PositionEntryRequest(

        @Schema(description = "이 회차에 지정가 주문을 걸 가격", example = "60000")
        BigDecimal price,

        @Schema(description = "이 회차에 배정한 비중. 100 이 전액이며 모든 회차의 합이 정확히 100 이어야 한다",
                example = "50")
        BigDecimal allocation) {

    PlannedEntry toEntry() {
        return new PlannedEntry(Price.of(price), Percentage.of(allocation));
    }
}
