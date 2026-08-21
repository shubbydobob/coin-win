package com.coinwin.projection.api;

import com.coinwin.projection.domain.ProjectionDistribution;
import com.coinwin.projection.domain.ProjectionSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * 같은 조건을 N 번 돌린 결과 분포.
 *
 * <p>중앙값 하나만 보면 안 된다. <b>하위 5% 와 상위 5% 사이의 폭이 이 응답의 요점이다</b> —
 * 기댓값이 같아도 그 폭은 조건마다 전혀 다르고, 실제로 겪게 되는 것은 평균이 아니라
 * 그 폭 어딘가의 한 경로다.
 */
@Schema(description = "N 회 시뮬레이션의 결과 분포",
        example = ProjectionApiExamples.MONTE_CARLO_RESPONSE)
public record MonteCarloResponse(

        @Schema(description = "시행 횟수", example = "1000")
        int runs,

        @Schema(description = "시행 한 번당 거래 수", example = "100")
        int tradesPerRun,

        @Schema(description = "거래당 기댓값을 R 배수로 표현한 값. 0 이면 본전, 음수면 지는 게임이다",
                example = "0.350000")
        BigDecimal expectancyPerTrade,

        @Schema(description = "가장 나빴던 시행의 최종 자산 (USDT)", example = "594.44")
        BigDecimal worstEquity,

        @Schema(description = "하위 5% 시행의 최종 자산. 운이 나쁠 때의 현실적인 바닥", example = "956.24")
        BigDecimal percentile5Equity,

        @Schema(description = "중앙값 시행의 최종 자산", example = "1538.24")
        BigDecimal medianEquity,

        @Schema(description = "상위 5% 시행의 최종 자산", example = "2474.47")
        BigDecimal percentile95Equity,

        @Schema(description = "가장 좋았던 시행의 최종 자산 (USDT)", example = "3980.53")
        BigDecimal bestEquity,

        @Schema(description = "최대낙폭의 중앙값 (%). 절반의 경우 이보다 깊게 빠진다",
                example = "14.9237")
        BigDecimal medianMaxDrawdown,

        @Schema(description = "가장 깊었던 최대낙폭 (%)", example = "42.9287")
        BigDecimal worstMaxDrawdown,

        @Schema(description = "최종 자산이 초기 자본에 못 미친 시행의 비율 (%)", example = "0.8000")
        BigDecimal lossProbability) {

    static MonteCarloResponse from(ProjectionDistribution distribution, ProjectionSpec spec) {
        return new MonteCarloResponse(
                distribution.runs(),
                spec.frequency().totalTrades(),
                spec.edge().expectancyPerTrade(),
                distribution.equityPercentile(0).value(),
                distribution.equityPercentile(5).value(),
                distribution.equityPercentile(50).value(),
                distribution.equityPercentile(95).value(),
                distribution.equityPercentile(100).value(),
                distribution.drawdownPercentile(50).value(),
                distribution.drawdownPercentile(100).value(),
                distribution.lossProbability().value());
    }
}
