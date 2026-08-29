package com.coinwin.readout.api;

import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 차트에 그릴 봉 하나.
 *
 * <p><b>{@code market} 의 캔들 DTO 를 다시 쓰지 않는다.</b> 그쪽은 {@code adapter.in.web} 에
 * 있고 여기서 참조하면 {@code readout.api → market.adapter} 가 되어 계층 규칙이 깨진다.
 * 두 DTO 가 같은 칸을 갖는 것은 중복이 아니라 <b>경계의 값</b>이다 — 한쪽이 칸을 늘려도 다른
 * 쪽이 따라 바뀌지 않는 것이 요점이다.
 */
@Schema(description = "차트에 그릴 봉 하나")
public record SeriesCandleResponse(
        @Schema(description = "봉이 열린 시각(UTC)", example = "2026-08-25T12:00:00Z")
        Instant openTime,
        @Schema(description = "시가", example = "79000.00")
        BigDecimal open,
        @Schema(description = "고가", example = "79500.00")
        BigDecimal high,
        @Schema(description = "저가", example = "78800.00")
        BigDecimal low,
        @Schema(description = "종가", example = "79200.00")
        BigDecimal close,
        @Schema(description = "거래량(BTC)", example = "1234.56700000")
        BigDecimal volume) {

    static List<SeriesCandleResponse> from(CandleSeries series) {
        return series.candles().stream().map(SeriesCandleResponse::from).toList();
    }

    private static SeriesCandleResponse from(Candle candle) {
        return new SeriesCandleResponse(
                candle.openTime(),
                candle.open().value(),
                candle.high().value(),
                candle.low().value(),
                candle.close().value(),
                candle.volume().value());
    }
}
