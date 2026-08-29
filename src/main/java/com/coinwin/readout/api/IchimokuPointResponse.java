package com.coinwin.readout.api;

import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 한 시점의 일목 다섯 선.
 *
 * <p><b>선행스팬 둘은 이미 밀린 값이다.</b> 변위 26 이 실제로는 25봉을 미는 것까지 계산기가
 * 확정해 두었다({@code docs/adr/014}). 화면이 다시 밀면 두 번 민다.
 */
@Schema(description = "한 시점의 일목균형표 값")
public record IchimokuPointResponse(
        @Schema(description = "봉 시각(UTC)", example = "2026-08-25T12:00:00Z")
        Instant at,
        @Schema(description = "전환선 (9)", example = "79100.00")
        BigDecimal conversionLine,
        @Schema(description = "기준선 (26)", example = "78420.00")
        BigDecimal baseLine,
        @Schema(description = "선행스팬 1", example = "78760.00")
        BigDecimal leadingSpanA,
        @Schema(description = "선행스팬 2", example = "77300.00")
        BigDecimal leadingSpanB,
        @Schema(description = "후행스팬. **없을 수 있다** — 밀 자리가 아직 없는 구간이다",
                example = "79880.00", nullable = true)
        BigDecimal laggingSpan) {

    static List<IchimokuPointResponse> from(List<IndicatorPoint<IchimokuValue>> points) {
        return points.stream().map(point -> {
            IchimokuValue value = point.value();
            return new IchimokuPointResponse(
                    point.at(),
                    value.conversionLine().value(),
                    value.baseLine().value(),
                    value.leadingSpanA().value(),
                    value.leadingSpanB().value(),
                    value.laggingSpan().map(price -> price.value()).orElse(null));
        }).toList();
    }
}
