package com.coinwin.journal.adapter.in.web;

import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.TradeQuery;
import com.coinwin.position.domain.Direction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 조회 조건 질의 파라미터. 전부 선택이고, 아무것도 주지 않으면 전체 조회다.
 *
 * <p>컨트롤러 메서드에 {@code @RequestParam} 다섯 개를 늘어놓지 않는 이유는 파라미터 한계
 * (4개)를 넘기 때문만이 아니다. 목록 조회와 집계 조회가 <b>정확히 같은 조건</b>을 받아야
 * 하는데, 파라미터를 늘어놓으면 두 시그니처가 언젠가 갈린다.
 */
@Schema(description = "거래 조회 조건. 전부 선택")
public record TradeQueryParams(

        @Schema(description = "이 시각부터 (포함)", example = "2026-08-01T00:00:00Z")
        Instant closedFrom,

        @Schema(description = "이 시각까지 (제외)", example = "2026-09-01T00:00:00Z")
        Instant closedTo,

        @Schema(description = "포지션 방향", example = "LONG")
        Direction direction,

        @Schema(description = "청산 이유", example = "HELD_PAST_STOP")
        ExitReason exitReason,

        @Schema(description = "계획 준수 여부", example = "false")
        Boolean followedPlan) {

    TradeQuery toQuery() {
        return TradeQuery.all()
                .closedBetween(closedFrom, closedTo)
                .withDirection(direction)
                .withExitReason(exitReason)
                .withFollowedPlan(followedPlan);
    }
}
