package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderWall;
import com.coinwin.market.domain.Ticker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 지금 얼마이고, 그 값에 얼마나 두껍게 쌓여 있나.
 *
 * <p>시세와 호가를 한 응답에 담는 이유는 포트가 둘을 함께 내는 이유와 같다 — 화면이 언제나
 * 함께 읽으므로 따로 내면 서로 다른 순간의 값을 나란히 놓게 된다.
 *
 * <p><b>파생값을 전부 서버가 낸다.</b> 스프레드·잔량 합·불균형을 화면이 계산하면 그 규칙이
 * 두 곳에 생긴다({@code docs/adr/020}).
 *
 * <p><b>방향을 말하지 않는다.</b> 불균형이 양수라는 것은 사실이고, 그것이 매수 신호인지는 이
 * 프로젝트가 답하지 않기로 한 질문이다({@code scope.md}).
 */
@Schema(description = "현재가와 호가", example = MarketApiExamples.ORDER_BOOK_RESPONSE)
public record OrderBookResponse(

        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "호가를 관측한 시각. 거래소가 준 값이다", example = "2026-08-23T09:00:00Z")
        Instant at,

        @Schema(description = "마지막 체결가", example = "76567.50")
        BigDecimal last,

        @Schema(description = "24시간 변동률 (%). 음수면 하락이다", example = "-1.009000")
        BigDecimal change24hPercent,

        @Schema(description = "24시간 최고가", example = "77590.60")
        BigDecimal high24h,

        @Schema(description = "24시간 최저가", example = "75588.00")
        BigDecimal low24h,

        @Schema(description = "24시간 거래량 (BTC)", example = "113033.30600000")
        BigDecimal volume24h,

        @Schema(description = "최우선 매수가", example = "76567.40")
        BigDecimal bestBid,

        @Schema(description = "최우선 매도가", example = "76567.50")
        BigDecimal bestAsk,

        @Schema(description = "최우선 매수와 매도의 간격", example = "0.10")
        BigDecimal spread,

        @Schema(description = "스프레드가 최우선 매도가의 몇 %인가", example = "0.0001")
        BigDecimal spreadPercent,

        @Schema(description = "매수 잔량 합 (BTC). 요청한 단수까지만 센다", example = "22.10000000")
        BigDecimal bidVolume,

        @Schema(description = "매도 잔량 합 (BTC)", example = "18.40000000")
        BigDecimal askVolume,

        @Schema(description = "(매수 잔량 - 매도 잔량) / 합. 양수면 매수가 두껍다. "
                + "방향을 뜻하지 않는다 — 호가는 취소될 수 있다", example = "0.0910")
        BigDecimal imbalance,

        @Schema(description = "매수 호가. 높은 값부터")
        List<PriceLevelResponse> bids,

        @Schema(description = "매도 호가. 낮은 값부터")
        List<PriceLevelResponse> asks,

        @Schema(description = """
                매수 쪽에서 가장 두꺼운 단. **평소보다 두꺼울 때만 있다** — 언제나 최댓값을
                내면 그것은 그냥 최댓값이고, 늘 떠 있는 표시는 아무것도 알려 주지 않는다.
                조건을 못 넘으면 null 이다.""", nullable = true)
        OrderWallResponse bidWall,

        @Schema(description = "매도 쪽에서 가장 두꺼운 단. **없을 수 있다**", nullable = true)
        OrderWallResponse askWall) {

    public OrderBookResponse {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    static OrderBookResponse from(OrderBook book, Ticker ticker) {
        return new OrderBookResponse(
                book.symbol().value(),
                book.at(),
                ticker.last().value(),
                ticker.change24hPercent(),
                ticker.high24h().value(),
                ticker.low24h().value(),
                ticker.volume24h().value(),
                book.bestBid().value(),
                book.bestAsk().value(),
                book.spread().value(),
                book.spreadPercent().value(),
                book.bidVolume().value(),
                book.askVolume().value(),
                book.imbalance(),
                PriceLevelResponse.from(book.bids()),
                PriceLevelResponse.from(book.asks()),
                wall(book.biggestBid()), wall(book.biggestAsk()));
    }

    /** 기준을 못 넘은 쪽은 {@code null} 이다. 0 으로 채우면 화면에 없는 벽이 생긴다. */
    private static OrderWallResponse wall(Optional<OrderWall> found) {
        return found.map(OrderWallResponse::from).orElse(null);
    }
}
