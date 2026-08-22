package com.coinwin.backtest.api;

import com.coinwin.backtest.domain.BacktestComparison;
import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.journal.domain.ClosedTrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 백테스트 한 번의 결과. */
@Schema(description = "백테스트 결과. 성적 요약 · 거래 목록 · 자산 곡선")
public record BacktestResultResponse(
        @Schema(description = "성적 요약")
        SummaryResponse summary,

        @Schema(description = "시간순 거래 전부. 요약만 보고 판단하지 않도록 원자료를 함께 낸다")
        List<TradeResponse> trades,

        @Schema(description = "거래마다 한 점씩. 첫 점은 거래 이전의 초기 자본이다")
        List<BigDecimal> equityCurve) {

    public BacktestResultResponse {
        trades = List.copyOf(trades);
        equityCurve = List.copyOf(equityCurve);
    }

    public static BacktestResultResponse from(BacktestResult result) {
        return new BacktestResultResponse(
                SummaryResponse.from(result),
                result.trades().stream().map(TradeResponse::from).toList(),
                result.equity().points().stream().map(point -> point.value()).toList());
    }

    @Schema(description = "성적 요약")
    public record SummaryResponse(
            @Schema(description = "청산까지 끝난 거래 수", example = "37")
            int totalTrades,

            @Schema(description = "승률 (%). 0 원으로 끝난 거래는 승리가 아니다", example = "62.1621")
            BigDecimal winRate,

            @Schema(description = "총이익 / 총손실. 진 거래가 없으면 null — "
                    + "손실이 없는 표본은 손익비를 말할 수 없다", example = "1.84", nullable = true)
            BigDecimal profitFactor,

            @Schema(description = "수수료·슬리피지를 뺀 순손익 (USDT)", example = "213.45")
            BigDecimal netPnl,

            @Schema(description = "최종 자산 (USDT)", example = "1013.45")
            BigDecimal finalEquity,

            @Schema(description = "직전 고점 대비 가장 깊었던 하락폭 (%). "
                    + "사람이 계획을 그만두는 지점은 도착점이 아니라 여기다", example = "8.3200")
            BigDecimal maxDrawdown) {

        static SummaryResponse from(BacktestResult result) {
            return new SummaryResponse(
                    result.totalTrades(),
                    result.winRate().value(),
                    result.profitFactor().orElse(null),
                    result.netPnl().value(),
                    result.finalEquity().value(),
                    result.maxDrawdown().value());
        }
    }

    @Schema(description = "거래 한 건")
    public record TradeResponse(
            @Schema(description = "방향", example = "LONG")
            String direction,

            @Schema(description = "첫 진입 체결 시각")
            Instant openedAt,

            @Schema(description = "청산 시각")
            Instant closedAt,

            @Schema(description = "체결 수량 가중 평단", example = "59105.00")
            BigDecimal averageEntryPrice,

            @Schema(description = "청산가. 슬리피지가 반영돼 있다", example = "61987.60")
            BigDecimal exitPrice,

            @Schema(description = "청산 이유. 백테스트는 계획을 어기지 않으므로 둘뿐이다",
                    example = "PLANNED_TARGET")
            String exitReason,

            @Schema(description = "진입 회차. 1 이면 2차 지정가가 채워지지 않은 채 끝났다",
                    example = "2")
            int filledEntries,

            @Schema(description = "수수료를 뺀 실현 손익 (USDT)", example = "38.20")
            BigDecimal realizedPnl,

            @Schema(description = "진입 근거. 어느 대의 어느 경계에서 무엇을 보고 들어갔는가",
                    example = "지지대 59000.00~59200.00 (터치 3회) 근단 반전 진입")
            String rationale) {

        static TradeResponse from(ClosedTrade trade) {
            return new TradeResponse(
                    trade.plan().direction().name(),
                    trade.openedAt(),
                    trade.closedAt(),
                    trade.averageEntryPrice().value(),
                    trade.closure().exit().price().value(),
                    trade.closure().reason().name(),
                    trade.entries().count(),
                    trade.realizedPnl().value(),
                    trade.context().rationale());
        }
    }

    /** 한 가지만 다른 두 실행. 필터나 비용이 값을 하는지는 비교로만 답할 수 있다. */
    @Schema(description = "기준 실행과 변경 실행을 나란히 놓은 결과")
    public record ComparisonResponse(
            @Schema(description = "기준 실행")
            BacktestResultResponse baseline,

            @Schema(description = "한 가지를 바꾼 실행")
            BacktestResultResponse variant,

            @Schema(description = "순손익 차이. 음수면 그 변경이 손해였다", example = "-42.10")
            BigDecimal pnlDifference,

            @Schema(description = "거래 수 차이. 필터를 켠 쪽이 줄면 음수", example = "-11")
            int tradeDifference) {

        public static ComparisonResponse from(BacktestComparison comparison) {
            return new ComparisonResponse(
                    BacktestResultResponse.from(comparison.baseline()),
                    BacktestResultResponse.from(comparison.variant()),
                    comparison.pnlDifference().value(),
                    comparison.tradeDifference());
        }
    }
}
