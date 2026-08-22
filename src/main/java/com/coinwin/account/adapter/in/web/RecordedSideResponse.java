package com.coinwin.account.adapter.in.web;

import com.coinwin.journal.domain.OpenTrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 대조의 기록 쪽. 내가 무엇을 하려 했고 실제로 얼마에 얼마나 들어갔는가. */
@Schema(description = "기록에 남아 있는 미청산 거래")
public record RecordedSideResponse(
        @Schema(description = "거래 식별자", example = "3f2a1c8e-5b7d-4e9f-a1b2-c3d4e5f60718")
        UUID tradeId,

        @Schema(description = "실제 체결 평단. 계획한 평단과 다를 수 있고 그 차이가 슬리피지다",
                example = "59500.00")
        BigDecimal averageEntryPrice,

        @Schema(description = "체결된 총수량", example = "0.10000000")
        BigDecimal quantity,

        @Schema(description = "계획한 모든 분할이 체결됐는가", example = "true")
        boolean fullyFilled,

        @Schema(description = "포지션이 열린 시각. 첫 체결이다",
                example = "2026-08-01T01:00:00Z")
        Instant openedAt) {

    static RecordedSideResponse from(OpenTrade trade) {
        return new RecordedSideResponse(
                trade.id().value(),
                trade.averageEntryPrice().value(),
                trade.quantity().value(),
                trade.fullyFilled(),
                trade.openedAt());
    }
}
