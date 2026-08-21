package com.coinwin.position.api;

import com.coinwin.position.domain.PositionAnalysis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 계획 하나에 대한 산출 결과.
 *
 * <p>{@code fillStates} 의 마지막 원소가 전량 체결 상태다. 1차 체결 상태와 나란히 놓는 것이
 * 이 응답의 요점이다 — 두 값의 차이가 분할 진입에서 놓치기 쉬운 리스크다.
 */
@Schema(description = "체결 상태별 리스크 산출 결과", example = PositionApiExamples.RESPONSE)
public record PositionAnalysisResponse(

        @Schema(description = "1건 체결부터 전량 체결까지 순서대로. 마지막 원소가 전량 체결 상태다")
        List<FillStateResponse> fillStates,

        @Schema(description = "전량 체결에 필요한 증거금 (USDT)", example = "31.47")
        BigDecimal requiredMargin,

        @Schema(description = "손익비. 익절 거리를 손절 거리로 나눈 값", example = "2.33")
        BigDecimal riskRewardRatio,

        @Schema(description = "손익비가 1.5 미만이라는 경고. 계획을 막지는 않는다", example = "false")
        boolean weakRiskReward,

        @Schema(description = "필요 증거금이 잔고를 넘어 이 계획을 열 수 없다는 경고", example = "false")
        boolean marginExceedsBalance) {

    public PositionAnalysisResponse {
        fillStates = List.copyOf(fillStates);
    }

    static PositionAnalysisResponse from(PositionAnalysis analysis) {
        return new PositionAnalysisResponse(
                analysis.fillStates().stream().map(FillStateResponse::from).toList(),
                analysis.requiredMargin().value(),
                analysis.riskRewardRatio(),
                analysis.weakRiskReward(),
                analysis.marginExceedsBalance());
    }
}
