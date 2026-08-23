package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MetricOutlier;
import com.coinwin.market.domain.Percentile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 한 지표의 지금 값과 그 값이 최근 표본에서 차지하는 위치.
 *
 * <p><b>{@code topPercent} 는 비어 있을 수 있다.</b> 표본이 모자라면 위치를 말하지 않는다 —
 * "표본 3개 중 상위 33%" 는 수치의 모양만 갖춘 거짓말이고, 손익비와 청산가에서 이미 두 번
 * 세운 규칙이다. 그때 {@code sampleCount} 가 왜 비었는지를 말해 준다.
 *
 * <p>비었을 때 {@code outlier} 는 거짓이다. <b>모르는 것은 이상치가 아니다</b> — 모른다는
 * 이유로 경고를 띄우면 그 경고는 곧 배경이 된다.
 */
@Schema(description = "한 지표의 평소 대비 위치")
public record MetricOutlierResponse(

        @Schema(description = "지표 종류", example = "FUNDING_RATE",
                allowableValues = {"FUNDING_RATE", "OPEN_INTEREST", "LONG_SHORT_RATIO"})
        String metric,

        @Schema(description = "지금 값. 펀딩비는 %, 미결제약정은 BTC, 롱숏비율은 무차원이다",
                example = "0.010000")
        BigDecimal current,

        @Schema(description = "최근 표본에서 위쪽으로부터의 비율 (%). "
                + "12 면 상위 12% 다. 표본이 모자라면 null",
                example = "12.0000", nullable = true)
        BigDecimal topPercent,

        @Schema(description = "양 끝 5% 안에 있는가. 위치를 모르면 거짓이다", example = "false")
        boolean outlier,

        @Schema(description = "위치를 재는 데 쓴 표본 수", example = "90")
        int sampleCount) {

    static MetricOutlierResponse from(MetricOutlier outlier) {
        return new MetricOutlierResponse(
                outlier.kind().name(),
                outlier.current(),
                outlier.position().map(Percentile::topPercent).map(percent -> percent.value())
                        .orElse(null),
                outlier.isOutlier(),
                outlier.sampleCount());
    }
}
