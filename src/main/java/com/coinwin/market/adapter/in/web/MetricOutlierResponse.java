package com.coinwin.market.adapter.in.web;

import com.coinwin.common.domain.Percentage;
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
 *
 * <p><b>{@code side} 는 언제나 있고 {@code neutralPercent} 는 아니다.</b> 어느 쪽 진영인가는
 * 중립점과 견주기만 하면 되므로 표본이 하나도 없어도 답이 나온다. 중립점이 눈금 어디에
 * 오는가는 눈금 자체가 표본이라 표본을 탄다.
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

        @Schema(description = "어느 쪽 진영인가. 중립점(펀딩비 0, 비율 1)보다 크면 LONG, "
                + "작으면 SHORT, 같으면 BALANCED 다. NONE 은 중립이 아니라 **축이 없다**는 "
                + "뜻이다 — 미결제약정은 크기이지 방향이 아니다. "
                + "**붐비는 쪽이라는 뜻이지 유리한 쪽이라는 뜻이 아니다**",
                example = "LONG",
                allowableValues = {"LONG", "SHORT", "BALANCED", "NONE"})
        String side,

        @Schema(description = "중립점이 눈금 어디에 오는가 (위쪽으로부터의 비율 %). "
                + "현재값과 같은 방식으로 잰 위치라 나란히 놓을 수 있다. "
                + "축이 없거나 표본이 모자라면 null",
                example = "63.0000", nullable = true)
        BigDecimal neutralPercent,

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
                outlier.position().map(Percentile::topPercent).map(Percentage::value).orElse(null),
                outlier.isOutlier(),
                outlier.sampleCount(),
                outlier.change().orElse(null),
                outlier.kind().recentWindow(),
                outlier.side().name(),
                outlier.neutralPosition().map(Percentile::topPercent).map(Percentage::value)
                        .orElse(null),
                outlier.samples());
    }
}
