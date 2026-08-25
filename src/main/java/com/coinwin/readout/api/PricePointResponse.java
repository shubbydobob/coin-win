package com.coinwin.readout.api;

import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.IndicatorPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 한 시점의 값 하나. 이동평균처럼 선이 하나인 지표가 쓴다. */
@Schema(description = "한 시점의 값 하나")
public record PricePointResponse(
        @Schema(description = "그 값이 속한 봉의 시각(UTC)", example = "2026-08-25T12:00:00Z")
        Instant at,
        @Schema(description = "값", example = "79120.45")
        BigDecimal value) {

    static List<PricePointResponse> from(List<IndicatorPoint<Price>> points) {
        return points.stream()
                .map(point -> new PricePointResponse(point.at(), point.value().value()))
                .toList();
    }
}
