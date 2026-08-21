package com.coinwin.ai.adapter.in.web;

import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 문장에서 읽어낸 계획 초안.
 *
 * <p>필드 이름이 {@code TradePlanRequest} 와 같다. <b>확인한 뒤 그대로 잘라 붙일 수 있어야
 * 하기 때문</b>이다. 초안은 저장되지도 계산되지도 않으므로, 다음 행동은 언제나 사용자가
 * 기존 API 를 부르는 것이다.
 *
 * <p>총수량이 없는 것은 빠뜨린 것이 아니다. 수량은 손절가와 리스크 예산이 결정하는 값이고
 * 그 계산은 {@code /api/position-plans/analysis} 가 한다.
 */
@Schema(description = "자연어에서 읽어낸 매매 계획 초안",
        example = AiApiExamples.PLAN_DRAFT_RESPONSE)
public record PlanDraftResponse(

        @Schema(description = "포지션 방향", example = "LONG")
        Direction direction,

        @Schema(description = "분할 진입 계획. 문장에 나온 회차 수 그대로다")
        List<EntryResponse> entries,

        @Schema(description = "손절가", example = "58000")
        BigDecimal stopLoss,

        @Schema(description = "익절가", example = "68000")
        BigDecimal takeProfit,

        @Schema(description = "레버리지 배수", example = "10")
        int leverage) {

    public PlanDraftResponse {
        entries = List.copyOf(entries);
    }

    @Schema(description = "분할 진입 한 회차")
    public record EntryResponse(

            @Schema(description = "이 회차의 지정가", example = "62000")
            BigDecimal price,

            @Schema(description = "이 회차에 넣을 비중. 50 은 50% 를 뜻한다", example = "50")
            BigDecimal allocation) {

        static EntryResponse from(PlannedEntry entry) {
            return new EntryResponse(entry.price().value(), entry.allocation().value());
        }
    }

    public static PlanDraftResponse from(PositionPlan plan) {
        return new PlanDraftResponse(
                plan.direction(),
                plan.entries().entries().stream().map(EntryResponse::from).toList(),
                plan.stopLoss().value(),
                plan.takeProfit().value(),
                plan.leverage());
    }
}
