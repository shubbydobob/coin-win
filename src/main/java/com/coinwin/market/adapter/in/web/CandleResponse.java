package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.Candle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** 캔들 하나. 주기는 캔들이 아니라 묶음의 성질이라 여기에 없다. */
@Schema(description = "한 구간의 시가·고가·저가·종가와 거래량")
public record CandleResponse(

        @Schema(description = "구간 시작 시각 (UTC)", example = "2026-08-01T00:00:00Z")
        Instant openTime,

        @Schema(description = "시가 (USDT)", example = "60000.00")
        BigDecimal open,

        @Schema(description = "고가 (USDT). 시가·저가·종가를 모두 감싼다", example = "61000.00")
        BigDecimal high,

        @Schema(description = "저가 (USDT)", example = "59000.00")
        BigDecimal low,

        @Schema(description = "종가 (USDT)", example = "60500.00")
        BigDecimal close,

        @Schema(description = "거래량 (BTC). 거래가 없던 구간은 0 이다", example = "1.50000000")
        BigDecimal volume) {

    static CandleResponse from(Candle candle) {
        return new CandleResponse(
                candle.openTime(),
                candle.open().value(),
                candle.high().value(),
                candle.low().value(),
                candle.close().value(),
                candle.volume().value());
    }
}
