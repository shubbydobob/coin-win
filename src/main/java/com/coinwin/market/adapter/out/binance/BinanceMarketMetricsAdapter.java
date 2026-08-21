package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.application.port.out.LoadMarketMetricsPort;
import com.coinwin.market.domain.FundingRate;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 펀딩비·미결제약정·롱숏비율을 바이낸스 공개 엔드포인트 세 곳에서 모아 온다.
 *
 * <p>세 번 호출한다는 사실이 이 어댑터 밖으로 새지 않는다. 포트가 약속한 것은 "한 시점의
 * 시장 상태" 하나이고, 그것을 어떻게 만드는지는 여기의 사정이다.
 *
 * <p>시각은 {@code premiumIndex} 가 준 것을 쓴다. 세 응답의 시각이 조금씩 다른데 그중 하나를
 * 골라야 한다면, 셋 중 가장 자주 갱신되는 펀딩비 쪽이 맞다.
 */
@Component
public class BinanceMarketMetricsAdapter implements LoadMarketMetricsPort {

    private static final String PREMIUM_INDEX = "/fapi/v1/premiumIndex";
    private static final String OPEN_INTEREST = "/fapi/v1/openInterest";
    private static final String LONG_SHORT_RATIO = "/futures/data/globalLongShortAccountRatio";

    /** 롱숏비율은 기간을 골라야 한다. 가장 짧은 5분이 "지금" 에 가장 가깝다. */
    private static final String RATIO_PERIOD = "5m";

    private final RestClient client;

    public BinanceMarketMetricsAdapter(RestClient binanceRestClient) {
        this.client = binanceRestClient;
    }

    @Override
    public MarketMetrics metricsFor(Symbol symbol) {
        PremiumIndex premium = fetch(PREMIUM_INDEX, symbol, PremiumIndex.class);
        OpenInterest openInterest = fetch(OPEN_INTEREST, symbol, OpenInterest.class);
        return new MarketMetrics(
                symbol,
                Instant.ofEpochMilli(premium.time()),
                FundingRate.ofFraction(new BigDecimal(premium.lastFundingRate())),
                Quantity.of(openInterest.openInterest()),
                new BigDecimal(latestRatio(symbol).longShortRatio()));
    }

    private <T> T fetch(String path, Symbol symbol, Class<T> responseType) {
        try {
            return client.get()
                    .uri(uri -> uri.path(path).queryParam("symbol", symbol.value()).build())
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스 %s 를 가져오지 못했다: %s".formatted(path, symbol.value()), e);
        }
    }

    /** 목록의 <b>마지막</b>이 가장 최근이다. 오름차순으로 오기 때문이다. */
    private LongShortRatio latestRatio(Symbol symbol) {
        LongShortRatio[] history = fetchRatioHistory(symbol);
        if (history == null || history.length == 0) {
            throw new BinanceResponseException(
                    "롱숏비율이 비어 있다: " + symbol.value());
        }
        return history[history.length - 1];
    }

    private LongShortRatio[] fetchRatioHistory(Symbol symbol) {
        try {
            return client.get()
                    .uri(uri -> uri.path(LONG_SHORT_RATIO)
                            .queryParam("symbol", symbol.value())
                            .queryParam("period", RATIO_PERIOD)
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(LongShortRatio[].class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스 롱숏비율을 가져오지 못했다: " + symbol.value(), e);
        }
    }
}
