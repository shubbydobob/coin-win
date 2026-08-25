package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 스트림이 밀어 준 호가를 언제 내주고 언제 내주지 않는가.
 *
 * <p><b>내주지 않는 쪽이 이 타입의 존재 이유다.</b> 스트림은 소리 없이 멎을 수 있고, 그때
 * 마지막 호가를 계속 내주면 화면이 몇 분 전 호가를 지금으로 읽는다 — REST 로 3초마다 묻던
 * 것보다 나쁘다.
 *
 * <p>시계를 손으로 움직인다. {@code Thread.sleep} 으로 낡히면 테스트가 3초를 쓰고, 그
 * 3초는 신선함 기준을 바꾸는 순간 함께 늘어난다.
 */
class StreamedOrderBookTest {

    private static final Symbol BTC = Symbol.of("BTCUSDT");

    private static final Duration FRESHNESS = Duration.ofSeconds(3);

    private Instant now = Instant.parse("2026-08-25T00:00:00Z");

    private final StreamedOrderBook streamed = new StreamedOrderBook(
            movableClock(),
            new BinanceStreamProperties(
                    "wss://localhost:1/ws", "BTCUSDT", 20,
                    Duration.ofSeconds(1), FRESHNESS, Duration.ofSeconds(10)));

    @Test
    void 밀어_준_것이_없으면_비어_있다() {
        assertThat(streamed.fresh(BTC, OrderBookDepth.DEFAULT)).isEmpty();
        assertThat(streamed.isFlowing()).isFalse();
    }

    @Test
    void 방금_밀려온_호가를_내준다() {
        streamed.push(book(BTC, 20));

        assertThat(streamed.fresh(BTC, OrderBookDepth.DEFAULT)).isPresent();
        assertThat(streamed.isFlowing()).isTrue();
    }

    @Test
    void 신선함_기준을_넘긴_호가는_내주지_않는다() {
        streamed.push(book(BTC, 20));
        now = now.plus(FRESHNESS).plusMillis(1);

        assertThat(streamed.fresh(BTC, OrderBookDepth.DEFAULT)).isEmpty();
        assertThat(streamed.isFlowing()).isFalse();
    }

    /** 경계는 신선한 쪽에 든다 — 3초짜리 기준에서 정확히 3초는 아직 낡은 것이 아니다. */
    @Test
    void 기준과_정확히_같은_나이는_아직_신선하다() {
        streamed.push(book(BTC, 20));
        now = now.plus(FRESHNESS);

        assertThat(streamed.fresh(BTC, OrderBookDepth.DEFAULT)).isPresent();
    }

    @Test
    void 다른_종목은_내주지_않는다() {
        streamed.push(book(BTC, 20));

        assertThat(streamed.fresh(Symbol.of("ETHUSDT"), OrderBookDepth.DEFAULT)).isEmpty();
    }

    @Test
    void 요청한_단수만큼_잘라서_내준다() {
        streamed.push(book(BTC, 20));

        OrderBook served = streamed.fresh(BTC, OrderBookDepth.of(5)).orElseThrow();

        assertThat(served.bids()).hasSize(5);
        assertThat(served.asks()).hasSize(5);
    }

    /** 스트림이 5단만 받고 있는데 20단을 물으면 채워 넣지 않는다. 부르는 쪽이 REST 로 간다. */
    @Test
    void 스트림보다_깊은_요청은_내주지_않는다() {
        streamed.push(book(BTC, 5));

        assertThat(streamed.fresh(BTC, OrderBookDepth.of(20))).isEmpty();
        assertThat(streamed.fresh(BTC, OrderBookDepth.of(5))).isPresent();
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

    private OrderBook book(Symbol symbol, int levels) {
        List<PriceLevel> bids = new ArrayList<>();
        List<PriceLevel> asks = new ArrayList<>();
        for (int step = 0; step < levels; step++) {
            bids.add(level(78562.70 - step * 0.10, 1.0));
            asks.add(level(78562.80 + step * 0.10, 1.0));
        }
        return new OrderBook(symbol, bids, asks, now);
    }

    private static PriceLevel level(double price, double quantity) {
        return new PriceLevel(
                Price.of(BigDecimal.valueOf(price)), Quantity.of(BigDecimal.valueOf(quantity)));
    }
}
