package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.out.LoadMetricHistoryPort;
import com.coinwin.market.domain.FundingRate;
import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 세 지표의 지난 값들을 바이낸스 공개 엔드포인트에서 읽는다. 키가 필요 없다.
 *
 * <p><b>펀딩비만 단위를 옮긴다.</b> 이력 엔드포인트는 비율({@code 0.00010000})로 주는데 현재값을
 * 담는 {@code FundingRate} 는 퍼센트로 들고 있다. 옮기지 않으면 <b>현재값과 표본이 서로 다른
 * 단위</b>가 되어 위치가 언제나 극단으로 나온다 — 그리고 그 값은 그럴듯해 보인다. 변환을
 * {@code FundingRate.ofFraction} 에 맡기는 이유는 그 규칙이 이미 거기 있기 때문이다.
 *
 * <p>세 메서드가 각각 다른 경로를 부르는 것이 포트를 셋으로 나눈 이유다. 하나가 실패해도
 * 나머지 둘은 읽힌다.
 */
@Component
public class BinanceMetricHistoryAdapter implements LoadMetricHistoryPort {

    /** 이력의 기간. 가장 짧은 5분이 "지금" 에 가장 가깝다. 펀딩비는 주기가 고정이라 안 쓴다. */
    private static final String PERIOD = "5m";

    private static final String KLINES = "/fapi/v1/klines";

    /** 캔들 배열에서 종가의 자리. */
    private static final int CLOSE = 4;

    private static final MetricSource<BinanceFundingRate> FUNDING = new MetricSource<>(
            BinanceFundingRate[].class,
            "/fapi/v1/fundingRate",
            false,
            rate -> FundingRate.ofFraction(new BigDecimal(rate.fundingRate())).value());

    private static final MetricSource<BinanceOpenInterestHistory> OPEN_INTEREST =
            new MetricSource<>(
                    BinanceOpenInterestHistory[].class,
                    "/futures/data/openInterestHist",
                    true,
                    entry -> new BigDecimal(entry.sumOpenInterest()));

    private static final MetricSource<LongShortRatio> LONG_SHORT = new MetricSource<>(
            LongShortRatio[].class,
            "/futures/data/globalLongShortAccountRatio",
            true,
            ratio -> new BigDecimal(ratio.longShortRatio()));

    private static final MetricSource<BinanceTakerRatio> TAKER = new MetricSource<>(
            BinanceTakerRatio[].class,
            "/futures/data/takerlongshortRatio",
            true,
            ratio -> new BigDecimal(ratio.buySellRatio()));

    private static final MetricSource<LongShortRatio> TOP_POSITION = new MetricSource<>(
            LongShortRatio[].class,
            "/futures/data/topLongShortPositionRatio",
            true,
            ratio -> new BigDecimal(ratio.longShortRatio()));

    private final RestClient client;

    public BinanceMetricHistoryAdapter(RestClient binanceRestClient) {
        this.client = binanceRestClient;
    }

    @Override
    public MetricHistory fundingRates(Symbol symbol, int limit) {
        return history(FUNDING, symbol, limit);
    }

    @Override
    public MetricHistory openInterest(Symbol symbol, int limit) {
        return history(OPEN_INTEREST, symbol, limit);
    }

    @Override
    public MetricHistory longShortRatios(Symbol symbol, int limit) {
        return history(LONG_SHORT, symbol, limit);
    }

    @Override
    public MetricHistory takerRatios(Symbol symbol, int limit) {
        return history(TAKER, symbol, limit);
    }

    @Override
    public MetricHistory topPositionRatios(Symbol symbol, int limit) {
        return history(TOP_POSITION, symbol, limit);
    }

    /**
     * 5분봉 종가. 다른 지표와 <b>같은 주기</b>여야 같은 창으로 변화를 잴 수 있다.
     *
     * <p>캔들 응답은 배열의 배열이고 종가가 다섯 번째다. 그 순서를 여기서 한 번만 읽는다.
     */
    @Override
    public MetricHistory prices(Symbol symbol, int limit) {
        String[][] klines = fetchKlines(symbol, limit);
        if (klines == null || klines.length == 0) {
            throw new BinanceResponseException("가격 이력이 비어 있다: " + symbol.value());
        }
        return new MetricHistory(
                Arrays.stream(klines).map(kline -> new BigDecimal(kline[CLOSE])).toList());
    }

    private String[][] fetchKlines(Symbol symbol, int limit) {
        try {
            return client.get()
                    .uri(uri -> uri.path(KLINES)
                            .queryParam("symbol", symbol.value())
                            .queryParam("interval", PERIOD)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(String[][].class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스 %s 를 가져오지 못했다: %s".formatted(KLINES, symbol.value()), e);
        }
    }

    private <T> MetricHistory history(MetricSource<T> source, Symbol symbol, int limit) {
        T[] response = fetch(source, symbol, limit);
        if (response == null || response.length == 0) {
            throw new BinanceResponseException(
                    "%s 이력이 비어 있다: %s".formatted(source.path(), symbol.value()));
        }
        return new MetricHistory(Arrays.stream(response).map(source.value()).toList());
    }

    private <T> T[] fetch(MetricSource<T> source, Symbol symbol, int limit) {
        try {
            return client.get()
                    .uri(uri -> {
                        var builder = uri.path(source.path())
                                .queryParam("symbol", symbol.value())
                                .queryParam("limit", limit);
                        if (source.needsPeriod()) {
                            builder = builder.queryParam("period", PERIOD);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(source.responseType());
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스 %s 를 가져오지 못했다: %s".formatted(source.path(), symbol.value()), e);
        }
    }

    /**
     * 한 지표를 어디서 어떻게 읽는가. 셋의 차이가 이 넷뿐이라는 것을 타입으로 만든 것이다.
     *
     * <p>파라미터로 늘어놓으면 {@code conventions.md} 의 네 개 한계를 넘고, 무엇보다 <b>세 지표의
     * 차이가 호출부에 흩어진다.</b> 상수 셋을 나란히 놓으면 그 차이가 한눈에 읽힌다 —
     * 펀딩비만 {@code needsPeriod} 가 거짓이고 값 변환이 다르다는 것.
     */
    private record MetricSource<T>(
            Class<T[]> responseType,
            String path,
            boolean needsPeriod,
            Function<T, BigDecimal> value) {
    }
}
