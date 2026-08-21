package com.coinwin.projection.api;

import com.coinwin.common.domain.Money;
import com.coinwin.projection.domain.EquityCurve;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 표본 경로 하나. {@code equity} 의 첫 값은 거래 이전의 초기 자본이다.
 *
 * <p>최종 자산과 최대낙폭을 나란히 싣는다. 도착점만 보면 이 경로가 얼마나 견디기 힘든
 * 경로였는지가 사라지기 때문이다.
 */
@Schema(description = "시드 하나가 만든 자산 곡선", example = ProjectionApiExamples.CURVE_RESPONSE)
public record EquityCurveResponse(

        @Schema(description = "거래 순서대로의 자산. 첫 값은 거래 이전의 초기 자본이다")
        List<BigDecimal> equity,

        @Schema(description = "거래 수. 자산 점의 수보다 하나 적다", example = "4")
        int trades,

        @Schema(description = "최종 자산 (USDT)", example = "881.89")
        BigDecimal finalEquity,

        @Schema(description = "직전 고점 대비 가장 깊었던 하락폭 (%)", example = "2.0002")
        BigDecimal maxDrawdown) {

    public EquityCurveResponse {
        equity = List.copyOf(equity);
    }

    static EquityCurveResponse from(EquityCurve curve) {
        return new EquityCurveResponse(
                curve.points().stream().map(Money::value).toList(),
                curve.trades(),
                curve.finalEquity().value(),
                curve.maxDrawdown().value());
    }
}
