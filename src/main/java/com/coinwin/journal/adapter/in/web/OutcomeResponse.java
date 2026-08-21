package com.coinwin.journal.adapter.in.web;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExitReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 어떻게 끝났는가. 닫히지 않은 거래에는 없다({@code null}).
 *
 * <p>{@code followedPlan} 과 {@code realizedPnl} 을 나란히 싣는 것이 이 응답의 요점이다.
 * 규칙을 지키고 진 거래와 어기고 이긴 거래를 한눈에 갈라 보게 하려는 것이다.
 */
@Schema(description = "청산 결과와 반사실")
public record OutcomeResponse(

        @Schema(description = "청산 시각", example = "2026-08-01T09:00:00Z")
        Instant closedAt,

        @Schema(description = "청산 체결가", example = "64000.00")
        BigDecimal exitPrice,

        @Schema(description = "왜 닫았는가", example = "PLANNED_TARGET")
        ExitReason exitReason,

        @Schema(description = "계획대로 닫혔는가. 손실이어도 참일 수 있다", example = "true")
        boolean followedPlan,

        @Schema(description = "포지션을 들고 있던 시간 (ISO-8601)", example = "PT8H")
        Duration holdingPeriod,

        @Schema(description = "비용 전 손익 (USDT)", example = "450.00")
        BigDecimal grossPnl,

        @Schema(description = "수수료와 펀딩비를 뺀 실현 손익 (USDT)", example = "443.80")
        BigDecimal realizedPnl,

        @Schema(description = "손절을 지켰다면 났을 손익. 실제 청산가·보유 기간과 무관하다",
                example = "-155.00")
        BigDecimal lossIfStopHonored,

        @Schema(description = "계획을 어겨서 얻은 것. 음수면 어긴 대가다", example = "598.80")
        BigDecimal costOfDeviation) {

    static OutcomeResponse from(ClosedTrade trade) {
        return new OutcomeResponse(
                trade.closedAt(),
                trade.closure().exit().price().value(),
                trade.closure().reason(),
                trade.followedPlan(),
                trade.holdingPeriod(),
                trade.grossPnl().value(),
                trade.realizedPnl().value(),
                trade.lossIfStopHonored().value(),
                trade.costOfDeviation().value());
    }
}
