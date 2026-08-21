package com.coinwin.journal.adapter.in.web;

import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.TradeIntervals;
import com.coinwin.journal.domain.TradeTally;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * 집계. <b>계획 준수 쪽과 위반 쪽이 나란히 실린다.</b>
 *
 * <p>합계를 먼저 보여 주고 준수 여부를 곁들이는 형태가 아닌 이유는, 규칙을 어기고 크게 이긴
 * 거래 하나가 전체를 흑자로 만들면서 "지금 방식이 통한다" 는 결론을 만들기 때문이다.
 */
@Schema(description = "계획 준수 여부로 가른 집계")
public record JournalSummaryResponse(

        @Schema(description = "전체 거래 수", example = "4")
        int totalTrades,

        @Schema(description = "전체 실현 손익 (USDT)", example = "288.80")
        BigDecimal totalRealizedPnl,

        @Schema(description = "계획대로 닫은 거래의 비율 (%)", example = "50.0000")
        BigDecimal planAdherence,

        @Schema(description = "계획을 지킨 거래들")
        TallyResponse followed,

        @Schema(description = "계획을 어긴 거래들")
        TallyResponse broken,

        @Schema(description = "어긴 거래들을 손절가에서 닫았다면 났을 손익 합", example = "-310.00")
        BigDecimal lossIfEveryStopHonored,

        @Schema(description = "계획을 어겨서 얻은 것의 합. 음수면 어기는 편이 손해였다",
                example = "-206.00")
        BigDecimal costOfDeviation,

        @Schema(description = "거래 사이의 간격")
        IntervalsResponse intervals) {

    static JournalSummaryResponse from(JournalSummary summary) {
        return new JournalSummaryResponse(
                summary.totalTrades(),
                summary.totalRealizedPnl().value(),
                summary.planAdherence().value(),
                TallyResponse.from(summary.followed()),
                TallyResponse.from(summary.broken()),
                summary.lossIfEveryStopHonored().value(),
                summary.costOfDeviation().value(),
                IntervalsResponse.from(summary.intervals()));
    }

    /** 한 무리의 성적. */
    @Schema(description = "거래 한 무리의 건수·손익·승률")
    public record TallyResponse(
            @Schema(description = "건수", example = "2") int trades,
            @Schema(description = "실현 손익 합 (USDT)", example = "288.80") BigDecimal realizedPnl,
            @Schema(description = "이긴 건수. 본전은 승리가 아니다", example = "1") int wins,
            @Schema(description = "진 건수", example = "1") int losses,
            @Schema(description = "승률 (%)", example = "50.0000") BigDecimal winRate) {

        static TallyResponse from(TradeTally tally) {
            return new TallyResponse(tally.trades(), tally.realizedPnl().value(),
                    tally.wins(), tally.losses(), tally.winRate().value());
        }
    }

    /** 직전 청산부터 다음 진입까지. 손실 직후 곧바로 다시 들어가는 습관이 여기 드러난다. */
    @Schema(description = "거래 사이의 간격")
    public record IntervalsResponse(
            @Schema(description = "센 간격의 수. 거래 수보다 하나 적다", example = "3") int gaps,
            @Schema(description = "가장 짧았던 간격 (ISO-8601)", example = "PT12H") Duration shortest,
            @Schema(description = "평균 간격 (ISO-8601)", example = "PT22H") Duration average,
            @Schema(description = "겹쳐서 간격을 셀 수 없었던 쌍의 수. 0 이 아니면 기록이 어긋난 것이다",
                    example = "0") int overlaps) {

        static IntervalsResponse from(TradeIntervals intervals) {
            return new IntervalsResponse(intervals.gaps(), intervals.shortest(),
                    intervals.average(), intervals.overlaps());
        }
    }
}
