package com.coinwin.account.adapter.in.web;

import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.domain.Price;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 대조의 거래소 쪽. 지금 실제로 열려 있는 것. */
@Schema(description = "거래소가 말하는 지금 이 순간의 포지션")
public record ExchangeSideResponse(
        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "거래소가 계산한 평단", example = "59500.00")
        BigDecimal entryPrice,

        @Schema(description = "보유 수량. 언제나 양수이고 방향은 따로 있다",
                example = "0.10000000")
        BigDecimal quantity,

        @Schema(description = """
                거래소가 계산한 청산가. 우리 계산과 대조할 수 있는 유일한 값이다.
                거래소가 청산 지점을 말할 수 없으면 null 이다 — 0 은 청산가가 아니라 '없음'이다.""",
                nullable = true, example = "53765.06")
        BigDecimal liquidationPrice,

        @Schema(description = "미실현 손익. 기록에는 없는 값이다 — 매 순간 달라지므로 기록의 대상이 아니다",
                example = "12.40")
        BigDecimal unrealizedPnl) {

    static ExchangeSideResponse from(ExchangePosition position) {
        return new ExchangeSideResponse(
                position.symbol().value(),
                position.entryPrice().value(),
                position.quantity().value(),
                position.liquidationPrice().map(Price::value).orElse(null),
                position.unrealizedPnl().value());
    }
}
