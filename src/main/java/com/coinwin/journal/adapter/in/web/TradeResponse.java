package com.coinwin.journal.adapter.in.web;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.Trade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 거래 한 건. <b>상태에 따라 뒤쪽 두 칸이 비어 있다.</b>
 *
 * <p>도메인의 세 타입을 JSON 다형성으로 옮기지 않고 {@code null} 로 평평하게 낸 이유는,
 * 프런트가 한 벌의 응답 타입만 알면 되게 하기 위해서다. {@code state} 가 어느 칸이 채워져
 * 있는지를 결정하며 그 대응은 어긋날 수 없다 — 아래 {@code switch} 가 sealed 타입 위에 있어
 * 새 상태가 생기면 컴파일되지 않는다.
 */
@Schema(description = "거래 한 건. state 에 따라 entry / outcome 이 비어 있다")
public record TradeResponse(

        @Schema(description = "거래 식별자", example = "2f1c4e6a-0000-4000-8000-000000000001")
        UUID id,

        @Schema(description = "PLANNED / OPEN / CLOSED", example = "CLOSED")
        String state,

        @Schema(description = "계획을 세운 시각", example = "2026-08-01T00:00:00Z")
        Instant plannedAt,

        @Schema(description = "진입 전에 세운 계획")
        PlanResponse plan,

        @Schema(description = "실제 진입 결과. PLANNED 상태에서는 null")
        EntryResultResponse entry,

        @Schema(description = "청산 결과. CLOSED 상태에서만 존재")
        OutcomeResponse outcome) {

    static TradeResponse from(Trade trade) {
        return switch (trade) {
            case PlannedTrade planned -> new TradeResponse(planned.id().value(), "PLANNED",
                    planned.plannedAt(), PlanResponse.from(planned.plan()), null, null);
            case OpenTrade open -> new TradeResponse(open.id().value(), "OPEN",
                    open.plannedAt(), PlanResponse.from(open.plan()),
                    EntryResultResponse.from(open.entries(), open.context()), null);
            case ClosedTrade closed -> new TradeResponse(closed.id().value(), "CLOSED",
                    closed.plannedAt(), PlanResponse.from(closed.plan()),
                    EntryResultResponse.from(closed.entries(), closed.context()),
                    OutcomeResponse.from(closed));
        };
    }
}
