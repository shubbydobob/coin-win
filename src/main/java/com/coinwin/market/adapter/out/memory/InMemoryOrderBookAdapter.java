package com.coinwin.market.adapter.out.memory;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 호가와 시세를 메모리에 담는 어댑터.
 *
 * <p>존재 이유는 <b>거래소 없이 서비스와 화면 테스트를 돌리기 위해서</b>다.
 * {@code InMemoryCandleAdapter} 와 같은 자리이고, 이 어댑터가 있어서 호가 포트가
 * "구현체가 둘 이상" 을 만족한다.
 *
 * <p><b>넣지 않은 종목은 실패한다.</b> 빈 호가를 내지 않는 것이 계약이다 — 빈 것과 없는 것은
 * 다른 사실이고, 그 구분이 무너지면 화면이 "호가가 비었다" 를 태연히 보여 준다.
 *
 * <p>Spring 애너테이션이 없다. 테스트가 직접 {@code new} 로 만든다.
 */
public class InMemoryOrderBookAdapter implements LoadOrderBookPort {

    private final Map<Symbol, OrderBook> books = new ConcurrentHashMap<>();
    private final Map<Symbol, Ticker> tickers = new ConcurrentHashMap<>();

    /** 기본 표본을 심은 어댑터. 계약 테스트가 이 상태에서 시작한다. */
    public static InMemoryOrderBookAdapter withSample(Symbol symbol, Instant at) {
        InMemoryOrderBookAdapter adapter = new InMemoryOrderBookAdapter();
        adapter.put(sampleBook(symbol, at));
        adapter.put(sampleTicker(symbol, at));
        return adapter;
    }

    public void put(OrderBook book) {
        books.put(book.symbol(), book);
    }

    public void put(Ticker ticker) {
        tickers.put(ticker.symbol(), ticker);
    }

    @Override
    public OrderBook orderBookFor(Symbol symbol, int depth) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            throw new ExternalDataUnavailableException("호가가 없다: " + symbol.value());
        }
        return new OrderBook(symbol, take(book.bids(), depth), take(book.asks(), depth), book.at());
    }

    @Override
    public Ticker tickerFor(Symbol symbol) {
        Ticker ticker = tickers.get(symbol);
        if (ticker == null) {
            throw new ExternalDataUnavailableException("시세가 없다: " + symbol.value());
        }
        return ticker;
    }

    private static List<PriceLevel> take(List<PriceLevel> levels, int depth) {
        return levels.subList(0, Math.min(depth, levels.size()));
    }

    private static OrderBook sampleBook(Symbol symbol, Instant at) {
        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();
        for (int step = 0; step < 20; step++) {
            bids.add(level(76567.40 - step * 0.10, 1.0 + step * 0.05));
            asks.add(level(76567.50 + step * 0.10, 0.8 + step * 0.05));
        }
        return new OrderBook(symbol, bids, asks, at);
    }

    private static Ticker sampleTicker(Symbol symbol, Instant at) {
        return new Ticker(
                symbol,
                Price.of("76567.50"),
                new BigDecimal("-1.009"),
                Price.of("77590.60"),
                Price.of("75588.00"),
                Quantity.of("113033.306"),
                at);
    }

    private static PriceLevel level(double price, double quantity) {
        return new PriceLevel(
                Price.of(BigDecimal.valueOf(price)), Quantity.of(BigDecimal.valueOf(quantity)));
    }
}
