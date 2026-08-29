package com.coinwin.readout.api;

import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.MacdValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 한 시점의 MACD 세 값. <b>가격이 아니라 가격의 차라 음수가 될 수 있다.</b> */
@Schema(description = "한 시점의 MACD 값")
public record MacdPointResponse(
        @Schema(description = "봉 시각(UTC)", example = "2026-08-25T12:00:00Z")
        Instant at,
        @Schema(description = "빠른 EMA(12) − 느린 EMA(26). 음수일 수 있다", example = "123.4567")
        BigDecimal macd,
        @Schema(description = "MACD 의 EMA(9)", example = "98.7654")
        BigDecimal signal,
        @Schema(description = "MACD − 시그널", example = "24.6913")
        BigDecimal histogram) {

    static List<MacdPointResponse> from(List<IndicatorPoint<MacdValue>> points) {
        return points.stream()
                .map(point -> new MacdPointResponse(
                        point.at(),
                        point.value().macd(),
                        point.value().signal(),
                        point.value().histogram()))
                .toList();
    }
}
