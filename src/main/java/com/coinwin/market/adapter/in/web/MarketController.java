package com.coinwin.market.adapter.in.web;

import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadMacroQuotesUseCase;
import com.coinwin.market.application.port.in.LoadMarketMetricsUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.in.LoadOutliersUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.OrderBookDepth;
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
    private final LoadOrderBookUseCase loadOrderBook;
    private final LoadOutliersUseCase loadOutliers;
    private final LoadMacroQuotesUseCase loadMacroQuotes;

    public MarketController(MarketUseCases useCases) {
        this.loadMarketData = useCases.loadMarketData();
        this.syncMarketData = useCases.syncMarketData();
        this.loadMetrics = useCases.loadMetrics();
        this.loadOrderBook = useCases.loadOrderBook();
        this.loadOutliers = useCases.loadOutliers();
        this.loadMacroQuotes = useCases.loadMacroQuotes();
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

    @Operation(
            summary = "현재가와 호가",
            description = """
                    지금 얼마이고 그 값에 얼마나 두껍게 쌓여 있는가. 거래소를 직접 때린다.

                    불균형이 양수라는 것은 매수 잔량이 더 많다는 **사실**이고 방향을 뜻하지
                    않는다 — 호가는 취소될 수 있고 큰 벽은 오히려 미끼인 경우가 많다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "현재가와 호가"),
        @ApiResponse(responseCode = "400", description = "종목 표기가 올바르지 않거나 호가 단수가 5 · 10 · 20 중 하나가 아니다"),
        @ApiResponse(responseCode = "503", description = "거래소에 닿지 못했다")
    })
    @GetMapping("/{symbol}/orderbook")
    public OrderBookResponse orderBook(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int depth) {
        Symbol parsed = Symbol.of(symbol);
        return OrderBookResponse.from(
                loadOrderBook.orderBook(parsed, OrderBookDepth.of(depth)),
                loadOrderBook.ticker(parsed));
    }

    @Operation(
            summary = "세 지표의 평소 대비 위치",
            description = """
                    펀딩비·미결제약정·롱숏비율이 최근 표본에서 어디쯤인가.

                    배수가 아니라 위치로 말한다 — 펀딩비는 부호가 바뀌어 배수가 무너진다.
                    표본이 모자라면 `topPercent` 가 null 이다. 말할 수 없는 것을 수치로 적지
                    않는다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "지표별 위치"),
        @ApiResponse(responseCode = "400", description = "종목 표기가 올바르지 않다"),
        @ApiResponse(responseCode = "503", description = "거래소에 닿지 못했다")
    })
    @GetMapping("/{symbol}/outliers")
    public MetricOutliersResponse outliers(@PathVariable String symbol) {
        return MetricOutliersResponse.from(loadOutliers.outliers(Symbol.of(symbol)));
    }

    @Operation(
            summary = "거시 자산 시세",
            description = """
                    나스닥 · 금 · 원유 · 국채 · 변동성. 전부 바이낸스에 상장된 TradFi
                    무기한이라 BTC 와 **같은 시계 · 같은 형식**이다.

                    **상관관계를 계산하지 않는다.** "나스닥이 오르니 BTC 도 오른다" 는 예측이고
                    이 프로젝트가 답하지 않기로 한 질문이다. 나란히 놓는 데까지만 한다.

                    못 읽은 종목은 목록에서 빠진다 — 하나가 나머지를 막지 않는다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "거시 자산 시세. 못 읽은 것은 빠진다")
    })
    @GetMapping("/macro")
    public MacroQuoteListResponse macro() {
        return MacroQuoteListResponse.from(loadMacroQuotes.macroQuotes());
    }
}
