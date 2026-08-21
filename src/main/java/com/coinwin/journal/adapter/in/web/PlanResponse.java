package com.coinwin.journal.adapter.in.web;

import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/** 저장된 계획. 세 상태 모두가 갖는다. */
@Schema(description = "진입 전에 세운 계획")
public record PlanResponse(

        @Schema(description = "포지션 방향", example = "LONG")
        Direction direction,

        @Schema(description = "분할 진입 계획. 순서가 진입 순서다")
        List<EntryResponse> entries,

        @Schema(description = "계획한 손절가", example = "58000.00")
        BigDecimal stopLoss,

        @Schema(description = "계획한 익절가", example = "64000.00")
        BigDecimal takeProfit,

        @Schema(description = "레버리지 배수", example = "10")
        int leverage,

        @Schema(description = "표시용 손익비. 버림이라 1.50 이상이면 경고 기준을 넘긴 것이다",
                example = "3.00")
        BigDecimal riskRewardRatio,

        @Schema(description = "손익비가 1.5 미만인가. 경고일 뿐 거부하지 않는다", example = "false")
        boolean weakRiskReward) {

    /** 넘어온 리스트를 그대로 들고 있지 않는다. 응답 객체가 직렬화되기 전에 원본이 바뀔 수 있다. */
    public PlanResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    static PlanResponse from(PositionPlan plan) {
        return new PlanResponse(
                plan.direction(),
                plan.entries().entries().stream().map(EntryResponse::from).toList(),
                plan.stopLoss().value(),
                plan.takeProfit().value(),
                plan.leverage(),
                plan.riskRewardRatio(),
                plan.weakRiskReward());
    }

    /** 계획된 한 회차. */
    @Schema(description = "분할 진입 한 회차")
    public record EntryResponse(
            @Schema(description = "지정가", example = "60000.00") BigDecimal price,
            @Schema(description = "비중 (%)", example = "50.0000") BigDecimal allocation) {

        static EntryResponse from(PlannedEntry entry) {
            return new EntryResponse(entry.price().value(), entry.allocation().value());
        }
    }
}
