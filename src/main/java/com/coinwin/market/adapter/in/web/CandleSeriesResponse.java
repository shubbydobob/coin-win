package com.coinwin.market.adapter.in.web;

import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 저장된 캔들 묶음.
 *
 * <p>{@code count} 를 따로 싣는 이유는 요청한 구간에 비해 몇 개가 왔는지가 <b>구멍의
 * 신호</b>이기 때문이다. 24시간을 1h 로 요청했는데 20개가 오면 4개가 아직 수집되지 않은 것이다.
 */
@Schema(description = "저장된 캔들 묶음. 시간 오름차순이고 같은 시각이 두 번 나타나지 않는다",
        example = MarketApiExamples.CANDLES_RESPONSE)
public record CandleSeriesResponse(

        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,

        @Schema(description = "캔들 주기", example = "1h")
        String interval,

        @Schema(description = "캔들 수. 요청 구간이 기대하는 수보다 적으면 수집에 구멍이 있다",
                example = "2")
        int count,

        @Schema(description = "캔들 목록")
        List<CandleResponse> candles) {

    public CandleSeriesResponse {
        candles = List.copyOf(candles);
    }

    static CandleSeriesResponse from(CandleQuery query, CandleSeries series) {
        return new CandleSeriesResponse(
                query.symbol().value(),
                query.interval().code(),
                series.size(),
                series.candles().stream().map(CandleResponse::from).toList());
    }
}
