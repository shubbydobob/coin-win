package com.coinwin.market.adapter.in.web;

import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadMarketMetricsUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시장 데이터 조회. 공개 엔드포인트만 쓰므로 API 키가 필요 없다.
 *
 * <p>조회와 수집을 나눈 것이 이 컨트롤러에서 가장 중요한 판단이다. {@code GET} 은 저장된
 * 것만 읽고 거래소를 때리지 않는다. 그래서 네트워크가 끊겨도 읽히고, 같은 요청이 같은 답을
 * 낸다. 채우는 것은 {@code POST .../sync} 가 명시적으로 한다.
 */
@RestController
@RequestMapping("/api/markets")
@Tag(name = "시장 데이터", description = "캔들 수집·조회와 펀딩비·미결제약정·롱숏비율")
public class MarketController {

    private final LoadMarketDataUseCase loadMarketData;
    private final SyncMarketDataUseCase syncMarketData;
    private final LoadMarketMetricsUseCase loadMetrics;

    public MarketController(
            LoadMarketDataUseCase loadMarketData,
            SyncMarketDataUseCase syncMarketData,
            LoadMarketMetricsUseCase loadMetrics) {
        this.loadMarketData = loadMarketData;
        this.syncMarketData = syncMarketData;
        this.loadMetrics = loadMetrics;
    }

    @Operation(
            summary = "저장된 캔들 조회",
            description = """
                    거래소를 때리지 않는다. 이미 저장된 것만 돌려준다.
                    구간은 반열림 [from, to) 이라 연속 조회에서 경계 캔들이 겹치지 않는다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "저장된 캔들. 구간에 없으면 빈 목록이다"),
        @ApiResponse(responseCode = "400", description = "종목 표기나 캔들 주기가 올바르지 않다"),
        @ApiResponse(responseCode = "422", description = "구간의 끝이 시작보다 앞이다")
    })
    @GetMapping("/{symbol}/candles")
    public CandleSeriesResponse candles(
            @PathVariable String symbol,
            @RequestParam String interval,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        CandleQuery query = query(symbol, interval, from, to);
        return CandleSeriesResponse.from(query, loadMarketData.candles(query));
    }

    @Operation(
            summary = "거래소에서 받아 증분 저장",
            description = """
                    이미 저장된 시각은 다시 세지 않는다. 같은 구간을 두 번 수집하면
                    두 번째 응답의 newlyStored 는 0 이다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "가져온 캔들 수와 저장된 수. 이미 있던 것은 다시 세지 않는다"),
        @ApiResponse(responseCode = "400", description = "종목 표기나 캔들 주기가 올바르지 않다"),
        @ApiResponse(responseCode = "422", description = "구간의 끝이 시작보다 앞이다"),
        @ApiResponse(responseCode = "503", description = "거래소에 닿지 못했다")
    })
    @PostMapping("/{symbol}/candles/sync")
    public CandleSyncResponse sync(
            @PathVariable String symbol,
            @RequestParam String interval,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        CandleQuery query = query(symbol, interval, from, to);
        return CandleSyncResponse.from(query, syncMarketData.sync(query));
    }

    @Operation(
            summary = "펀딩비·미결제약정·롱숏비율",
            description = """
                    세 값을 한 시점으로 묶어 돌려준다. 따로 조회하면 서로 다른 시각의 값을
                    나란히 놓고 판단하게 된다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "펀딩비 · 미결제약정 · 롱숏비율"),
        @ApiResponse(responseCode = "400", description = "종목 표기가 올바르지 않다"),
        @ApiResponse(responseCode = "503", description = "거래소에 닿지 못했다")
    })
    @GetMapping("/{symbol}/metrics")
    public MarketMetricsResponse metrics(@PathVariable String symbol) {
        return MarketMetricsResponse.from(loadMetrics.metrics(Symbol.of(symbol)));
    }

    private static CandleQuery query(String symbol, String interval, Instant from, Instant to) {
        return new CandleQuery(Symbol.of(symbol), CandleInterval.ofCode(interval),
                new TimeRange(from, to));
    }
}
