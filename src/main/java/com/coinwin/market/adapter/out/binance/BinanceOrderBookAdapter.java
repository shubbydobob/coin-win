package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 호가와 24시간 시세를 바이낸스 공개 엔드포인트에서 읽는다. 키가 필요 없다.
 *
 * <p><b>시각은 거래소가 준 값을 쓴다.</b> 우리 시계로 찍지 않는다 — 개발 기계가 33초 앞서
 * 있던 것을 {@code BinanceServerClock} 이 잡은 자리와 같은 이유다. 호가는 초 단위로 달라지므로
 * 그 어긋남이 그대로 "언제 본 값인가" 에 실린다.
 *
 * <p>{@code depth} 응답의 시각 필드가 비어 있을 때만 로컬 시계로 물러선다. 관측 시각 없이
 * 호가를 만들 수는 없기 때문이다. 그 시계를 주입받는 이유는 테스트가 시각을 고정할 수 있어야
 * 하기 때문이다.
 */
@Component
public class BinanceOrderBookAdapter implements LoadOrderBookPort {

    private static final String DEPTH = "/fapi/v1/depth";

    private static final String TICKER = "/fapi/v1/ticker/24hr";

    private final RestClient client;

    private final Clock clock;

    public BinanceOrderBookAdapter(RestClient binanceRestClient, Clock clock) {
        this.client = binanceRestClient;
        this.clock = clock;
    }

    @Override
    public OrderBook orderBookFor(Symbol symbol, int depth) {
        BinanceDepth response = fetch(
                uri -> uri.path(DEPTH)
                        .queryParam("symbol", symbol.value())
                        .queryParam("limit", depth)
                        .build(),
                BinanceDepth.class,
                DEPTH,
                symbol);
        if (response == null || response.bids() == null || response.asks() == null) {
            throw new BinanceResponseException("호가가 비어 있다: " + symbol.value());
        }
        return new OrderBook(symbol, levels(response.bids()), levels(response.asks()), at(response));
    }

    @Override
    public Ticker tickerFor(Symbol symbol) {
        BinanceTicker response = fetch(
                uri -> uri.path(TICKER).queryParam("symbol", symbol.value()).build(),
                BinanceTicker.class,
                TICKER,
                symbol);
        if (response == null || response.lastPrice() == null) {
            throw new BinanceResponseException("시세가 비어 있다: " + symbol.value());
        }
        return new Ticker(
                symbol,
                Price.of(response.lastPrice()),
                new BigDecimal(response.priceChangePercent()),
                Price.of(response.highPrice()),
                Price.of(response.lowPrice()),
                Quantity.of(response.volume()),
                Instant.ofEpochMilli(response.closeTime()));
    }

    private static List<PriceLevel> levels(List<List<String>> raw) {
        return raw.stream()
                .map(level -> new PriceLevel(
                        Price.of(BinanceDepth.priceOf(level)),
                        Quantity.of(BinanceDepth.quantityOf(level))))
                .toList();
    }

    private Instant at(BinanceDepth response) {
        return response.eventTime() == null
                ? clock.instant()
                : Instant.ofEpochMilli(response.eventTime());
    }

    private <T> T fetch(
            Function<UriBuilder, URI> uri, Class<T> responseType, String path, Symbol symbol) {
        try {
            return client.get().uri(uri).retrieve().body(responseType);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스 %s 를 가져오지 못했다: %s".formatted(path, symbol.value()), e);
        }
    }
}
