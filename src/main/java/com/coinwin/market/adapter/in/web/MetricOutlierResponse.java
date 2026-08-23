package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.MetricOutlier;
import com.coinwin.market.domain.Percentile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

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
                allowableValues = {
                    "FUNDING_RATE", "OPEN_INTEREST", "LONG_SHORT_RATIO",
                    "TAKER_RATIO", "TOP_POSITION_RATIO", "PRICE"
                })
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
        int sampleCount,

        @Schema(description = "정해진 창에서의 변화. 대부분 비율(0.032 = 3.2% 증가)이고 "
                + "펀딩비만 차이(%p)다 — 부호가 바뀌는 값에서 비율이 무너지기 때문이다. "
                + "표본이 창보다 적거나 0 에서 출발했으면 null",
                example = "-0.032000", nullable = true)
        BigDecimal change,

        @Schema(description = "변화를 잰 창의 길이(표본 개수). 지표마다 다르다", example = "6")
        int changeWindow,

        @Schema(description = "표본 시계열. 화면이 스파크라인을 그리는 데 쓴다. "
                + "위치와 변화율만으로는 서서히인가 급격한가가 사라진다")
        List<BigDecimal> samples) {

    public MetricOutlierResponse {
        samples = List.copyOf(samples);
    }

    static MetricOutlierResponse from(MetricOutlier outlier) {
        return new MetricOutlierResponse(
                outlier.kind().name(),
                outlier.current(),
                outlier.position().map(Percentile::topPercent).map(percent -> percent.value())
                        .orElse(null),
                outlier.isOutlier(),
                outlier.sampleCount(),
                outlier.change().orElse(null),
                outlier.kind().recentWindow(),
                outlier.samples());
    }
}
