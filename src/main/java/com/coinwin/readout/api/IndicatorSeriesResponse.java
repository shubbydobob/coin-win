package com.coinwin.readout.api;

import com.coinwin.readout.domain.TimeframeSeries;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 한 주기의 캔들과 지표 곡선.
 *
 * <p><b>판독({@code /api/readout/{symbol}})과 다른 것을 답한다.</b> 저쪽은 "지금 어디에 서
 * 있나" 이고 이쪽은 "어떻게 여기까지 왔나" 다. 화면이 구름을 수평선으로 그리고 있던 이유가
 * 저쪽 응답에 봉 하나의 값밖에 없었기 때문이다.
 *
 * <p><b>한 응답에 캔들까지 담는다.</b> 따로 부르면 캔들과 지표가 서로 다른 순간의 것이 되고,
 * 그러면 마지막 봉 위에 놓인 점이 그 봉의 값이 아니게 된다.
 *
 * <p><b>지표마다 길이가 다르다.</b> 200 이동평균은 200봉째부터, 일목은 77봉째부터 값을 갖는다.
 * 앞을 채우지 않았고 점마다 시각이 달려 있으므로 그리는 쪽이 맞춘다 — <b>빈 목록도 정상</b>이며
 * 그 주기에 봉이 모자랐다는 뜻이다.
 */
@Schema(description = "한 주기의 캔들과 지표 곡선")
public record IndicatorSeriesResponse(
        @Schema(description = "종목", example = "BTCUSDT")
        String symbol,
        @Schema(description = "캔들 주기", example = "4h",
                allowableValues = {"1m", "5m", "15m", "1h", "4h", "1d", "1w"})
        String interval,
        @Schema(description = "봉 수", example = "300")
        int count,
        @Schema(description = "봉")
        List<SeriesCandleResponse> candles,
        @Schema(description = "일목균형표. **봉이 모자라면 빈 목록이다**")
        List<IchimokuPointResponse> ichimoku,
        @Schema(description = "볼린저 밴드. **봉이 모자라면 빈 목록이다**")
        List<BollingerPointResponse> bollinger,
        @Schema(description = "이동평균선들. 구간마다 하나씩")
        List<MovingAverageSeriesResponse> movingAverages,
        @Schema(description = "RSI(14). 0~100. **봉이 모자라면 빈 목록이다**")
        List<PricePointResponse> rsi,
        @Schema(description = "MACD(12/26/9). **봉이 모자라면 빈 목록이다**")
        List<MacdPointResponse> macd,

        @Schema(description = """
                지표마다 지금 어느 쪽에 서 있는가. **유리한 쪽이 아니라 관측이다** —
                전부 정의로 정해지는 사실이고 그것이 계속된다는 뜻은 없다.""")
        List<IndicatorStanceResponse> stances) {

    /** 목록을 그대로 들고 있으면 밖에서 바꿀 수 있다. 다른 응답 DTO 와 같은 처리다. */
    public IndicatorSeriesResponse {
        candles = List.copyOf(candles);
        ichimoku = List.copyOf(ichimoku);
        bollinger = List.copyOf(bollinger);
        movingAverages = List.copyOf(movingAverages);
        rsi = List.copyOf(rsi);
        macd = List.copyOf(macd);
        stances = List.copyOf(stances);
    }

    public static IndicatorSeriesResponse from(String symbol, TimeframeSeries series) {
        return new IndicatorSeriesResponse(
                symbol,
                series.interval().code(),
                series.candles().size(),
                SeriesCandleResponse.from(series.candles()),
                IchimokuPointResponse.from(series.ichimoku()),
                BollingerPointResponse.from(series.bollinger()),
                series.movingAverages().stream()
                        .map(line -> new MovingAverageSeriesResponse(
                                line.period(), PricePointResponse.from(line.points())))
                        .toList(),
                series.rsi().stream()
                        .map(point -> new PricePointResponse(point.at(), point.value().value()))
                        .toList(),
                MacdPointResponse.from(series.macd()),
                IndicatorStanceResponse.from(series.stances()));
    }
}
