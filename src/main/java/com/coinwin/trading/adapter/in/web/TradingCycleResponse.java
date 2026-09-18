package com.coinwin.trading.adapter.in.web;

import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.RiskVerdict;
import com.coinwin.trading.domain.TradingCycle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 봇이 한 번 깨어나서 한 일.
 *
 * <p><b>막힌 것과 낸 것을 다른 목록에 담는다.</b> 한 목록에 섞으면 "봇이 왜 안 들어갔나" 가
 * 답할 수 없는 질문이 된다.
 */
@Schema(description = "봇이 한 번 깨어나서 한 일의 전부")
public record TradingCycleResponse(
        @Schema(description = "이 사이클이 본 시각. 거래소가 말한 것을 그대로 쓴다",
                example = "2026-09-18T03:00:00Z")
        Instant at,

        @Schema(description = """
                주문이 어디로 갔나. PAPER 는 장부에만 적고 돈이 들지 않는다.
                TESTNET 은 가짜 돈, LIVE 만 진짜 돈이다.""",
                example = "PAPER", allowableValues = {"PAPER", "TESTNET", "LIVE"})
        String mode,

        @Schema(description = "판단한 전략의 이름", example = "가만히 있기")
        String strategy,

        @Schema(description = "실제로 브로커에 닿은 주문")
        List<PlacedOrderResponse> placed,

        @Schema(description = "한계에 걸려 나가지 못한 주문과 그 이유")
        List<RejectedOrderResponse> rejected,

        @Schema(description = """
                지운 주문의 식별자. 손절을 본전으로 옮길 때 옛 손절이 여기 들어간다.
                안 적으면 규칙이 옮긴 것과 누가 지운 것이 기록에서 구별되지 않는다.""")
        List<String> cancelled,

        @Schema(description = """
                봇이 멈춘 이유. 있으면 전략에게 묻지도 않았다는 뜻이다.
                멈추지 않았으면 null 이다 — 빈 문자열로 적으면 '이유 없이 멈췄다'가 된다.""",
                nullable = true, example = "누적 손실 한계를 넘었다. 사람이 켜야 다시 돈다")
        String halted,

        @Schema(description = "아무 일도 없었는가. 대부분의 사이클이 그렇다", example = "true")
        boolean quiet) {

    public TradingCycleResponse {
        placed = List.copyOf(placed);
        rejected = List.copyOf(rejected);
        cancelled = List.copyOf(cancelled);
    }

    public static TradingCycleResponse from(TradingCycle cycle) {
        return new TradingCycleResponse(
                cycle.at(),
                cycle.mode().name(),
                cycle.strategy(),
                cycle.placed().stream().map(PlacedOrderResponse::from).toList(),
                cycle.rejected().stream().map(RejectedOrderResponse::from).toList(),
                cycle.cancelled().stream().map(id -> id.value()).toList(),
                cycle.halted().orElse(null),
                cycle.quiet());
    }

    /** 실제로 낸 주문 하나. */
    @Schema(description = "브로커에 닿은 주문 하나")
    public record PlacedOrderResponse(
            @Schema(description = "브로커가 정한 식별자", example = "paper-1")
            String id,

            @Schema(description = "어느 방향의 포지션에 대한 주문인가. 매수/매도가 아니다",
                    example = "LONG", allowableValues = {"LONG", "SHORT"})
            String position,

            @Schema(description = """
                    ENTRY 는 시장가 진입, EXIT 은 지금 닫기,
                    STOP_LOSS 와 TAKE_PROFIT 은 트리거 주문이다.""",
                    example = "ENTRY",
                    allowableValues = {"ENTRY", "STOP_LOSS", "TAKE_PROFIT", "EXIT"})
            String kind,

            @Schema(description = "닫을 수량. null 이면 전량이다 — 0 으로 적으면 전량과 "
                    + "'아무것도 안 닫음'이 같은 값이 된다",
                    nullable = true, example = "0.01000000")
            BigDecimal quantity,

            @Schema(description = "트리거 가격. 시장가 주문은 null 이다",
                    nullable = true, example = "77000.00")
            BigDecimal triggerPrice,

            @Schema(description = "체결가. 트리거 주문은 걸려만 있으므로 null 이다",
                    nullable = true, example = "78015.60")
            BigDecimal fillPrice) {

        static PlacedOrderResponse from(PlacedOrder order) {
            return new PlacedOrderResponse(
                    order.id().value(),
                    order.intent().position().name(),
                    order.intent().kind().name(),
                    order.intent().quantity().map(amount -> amount.value()).orElse(null),
                    order.intent().trigger().map(price -> price.value()).orElse(null),
                    order.fillPrice().map(price -> price.value()).orElse(null));
        }
    }

    /** 안전장치가 막은 주문 하나. */
    @Schema(description = "한계에 걸려 나가지 못한 주문")
    public record RejectedOrderResponse(
            @Schema(description = "어느 방향의 포지션에 대한 주문이었나", example = "LONG",
                    allowableValues = {"LONG", "SHORT"})
            String position,

            @Schema(description = "무슨 주문이었나", example = "ENTRY",
                    allowableValues = {"ENTRY", "STOP_LOSS", "TAKE_PROFIT", "EXIT"})
            String kind,

            @Schema(description = "왜 막혔나. 사람이 읽는 한 문장이다",
                    example = "명목 2340.00 가 계좌의 2배 한계를 넘는다")
            String reason) {

        static RejectedOrderResponse from(RiskVerdict.Rejected rejected) {
            return new RejectedOrderResponse(
                    rejected.intent().position().name(),
                    rejected.intent().kind().name(),
                    rejected.reason());
        }
    }
}
