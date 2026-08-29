package com.coinwin.readout.api;

import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 한 시점의 볼린저 세 선. 중심선은 20봉 단순이동평균이라 이동평균 20 과 같은 값이다. */
@Schema(description = "한 시점의 볼린저 밴드 값")
public record BollingerPointResponse(
        @Schema(description = "봉 시각(UTC)", example = "2026-08-25T12:00:00Z")
        Instant at,
        @Schema(description = "상단", example = "80120.00")
        BigDecimal upper,
        @Schema(description = "중심. 20봉 단순이동평균이다", example = "78900.00")
        BigDecimal middle,
        @Schema(description = "하단", example = "77680.00")
        BigDecimal lower) {

    static List<BollingerPointResponse> from(List<IndicatorPoint<BollingerValue>> points) {
        return points.stream()
                .map(point -> new BollingerPointResponse(
                        point.at(),
                        point.value().upper().value(),
                        point.value().middle().value(),
                        point.value().lower().value()))
                .toList();
    }
}
