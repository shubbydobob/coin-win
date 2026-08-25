package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 스트림이 있으면 거래소를 부르지 않고, 없으면 부른다.
 *
 * <p><b>"부르지 않는다" 를 어떻게 증명하는가.</b> 페이크 거래소에 호가를 등록하지 않은 채로
 * 둔다 — REST 로 갔다면 404 를 만나 실패한다. 호출 횟수를 세는 대신 이렇게 하는 이유는,
 * 세는 장치가 곧 <b>검사 대상이 아닌 코드</b>이고 그것이 틀리면 테스트가 조용히 통과하기
 * 때문이다.
 */
class BinanceOrderBookAdapterTest {

    private static final Symbol BTC = Symbol.of("BTCUSDT");

    private static final Duration FRESHNESS = Duration.ofSeconds(3);

    private Instant now = Instant.parse("2026-08-25T00:00:00Z");

    private FakeBinanceServer exchange;
    private StreamedOrderBook streamed;
    private BinanceOrderBookAdapter adapter;

    @BeforeEach
    void 페이크_거래소를_띄운다() {
        exchange = new FakeBinanceServer();
        streamed = new StreamedOrderBook(movableClock(), properties());
        adapter = new BinanceOrderBookAdapter(
                RestClient.builder().baseUrl(exchange.baseUrl()).build(), movableClock(), streamed);
    }

    @AfterEach
    void 페이크_거래소를_닫는다() {
        exchange.close();
    }

    @Test
    void 스트림이_신선하면_거래소를_부르지_않는다() {
        streamed.push(streamedBook());

        OrderBook book = adapter.orderBookFor(BTC, OrderBookDepth.of(5));

        assertThat(book.bestBid().value()).isEqualByComparingTo("78562.70");
    }

    @Test
    void 스트림이_없으면_거래소에_묻는다() {
        거래소가_호가를_준다();

        OrderBook book = adapter.orderBookFor(BTC, OrderBookDepth.of(5));

        assertThat(book.bestBid().value()).isEqualByComparingTo("70000.10");
    }

    /** 스트림이 소리 없이 멎은 경우다. 마지막 호가를 계속 내주지 않고 거래소에 다시 묻는다. */
    @Test
    void 스트림이_낡으면_거래소에_묻는다() {
        streamed.push(streamedBook());
        거래소가_호가를_준다();
        now = now.plus(FRESHNESS).plusMillis(1);

        OrderBook book = adapter.orderBookFor(BTC, OrderBookDepth.of(5));

        assertThat(book.bestBid().value()).isEqualByComparingTo("70000.10");
    }

    /** 물러설 곳까지 없으면 실패한다. 빈 호가를 지어내지 않는다. */
    @Test
    void 스트림도_거래소도_없으면_실패한다() {
        assertThatThrownBy(() -> adapter.orderBookFor(BTC, OrderBookDepth.of(5)))
                .isInstanceOf(RuntimeException.class);
    }

    private void 거래소가_호가를_준다() {
        exchange.respondWith("/fapi/v1/depth", """
                {"lastUpdateId":1,"E":1787592539515,
                 "bids":[["70000.10","1.000"]],"asks":[["70000.20","1.000"]]}""");
    }

    private OrderBook streamedBook() {
        return new OrderBook(
                BTC,
                bids(),
                asks(),
                now);
    }

    /** 스트림은 20단을 받지만 화면이 5단을 물을 수 있다. 담당할 수 있는 깊이여야 내준다. */
    private static List<PriceLevel> bids() {
        return List.of(level("78562.70"), level("78562.60"), level("78562.50"),
                level("78562.40"), level("78562.30"));
    }

    private static List<PriceLevel> asks() {
        return List.of(level("78562.80"), level("78562.90"), level("78563.00"),
                level("78563.10"), level("78563.20"));
    }

    private static PriceLevel level(String price) {
        return new PriceLevel(Price.of(price), Quantity.of("1.000"));
    }

    private BinanceStreamProperties properties() {
        return new BinanceStreamProperties(
                "wss://localhost:1/ws", "BTCUSDT", 20,
                Duration.ofSeconds(1), FRESHNESS, Duration.ofSeconds(10));
    }

    private Clock movableClock() {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }
}
