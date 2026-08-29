package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.Symbol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 진짜 거래소에 붙어 호가가 실제로 밀려오는지 본다.
 *
 * <p><b>이 확인이 없으면 아무것도 증명되지 않는다.</b> 단위 테스트는 "이런 메시지가 오면 우리는
 * 이렇게 한다" 까지만 말한다 — 거래소가 그 메시지를 실제로 보내는지는 다른 질문이고, 이
 * 저장소는 정확히 그 자리에서 두 번 데였다(스키마 덮어쓰기, `-4021` 호가 단수).
 *
 * <p><b>그리고 이 기능 자체가 그 함정 위에 서 있다.</b> 같은 호스트의 {@code @ticker} 스트림은
 * 구독을 받아 주고 {@code LIST_SUBSCRIPTIONS} 에도 나오면서 <b>한 건도 보내지 않는다</b>
 * (2026-08-25 확인). 구독이 성공했다는 것은 값이 온다는 뜻이 아니다.
 *
 * <p>{@code check} 는 네트워크 없이 돌아야 하므로 {@code crosscheck} 로 뗀다.
 * {@code .\gradlew.bat crossCheck} 가 돌린다.
 */
@Tag("crosscheck")
class BinanceDepthStreamCrossCheckTest {

    private static final Symbol BTC = Symbol.of("BTCUSDT");

    /** 100ms 마다 오는 것이라 넉넉하다. 이만큼 기다려도 안 오면 그것이 사실이다. */
    private static final Duration PATIENCE = Duration.ofSeconds(20);

    private static final BinanceStreamProperties PROPERTIES = new BinanceStreamProperties(
            "wss://fstream.binance.com/ws", "BTCUSDT", 20,
            Duration.ofSeconds(3), Duration.ofSeconds(3), Duration.ofSeconds(10));

    @Test
    void 거래소가_호가를_밀어_주고_어댑터가_REST_없이_낸다() throws InterruptedException {
        Clock clock = Clock.systemUTC();
        StreamedOrderBook streamed = new StreamedOrderBook(clock, PROPERTIES);
        BinanceDepthStream stream = new BinanceDepthStream(streamed, PROPERTIES, clock);

        stream.afterPropertiesSet();
        try {
            흐를_때까지_기다린다(streamed);
            OrderBook book = REST_가_막힌_어댑터(clock, streamed)
                    .orderBookFor(BTC, OrderBookDepth.DEFAULT);

            System.out.printf("최우선 매수 %s / 매도 %s · 스프레드 %s (%s%%)%n",
                    book.bestBid().value(), book.bestAsk().value(),
                    book.spread().value(), book.spreadPercent().value());
            System.out.printf("불균형 %s · 관측 시각 %s (지금과 %dms 차이)%n",
                    book.imbalance(), book.at(),
                    Duration.between(book.at(), clock.instant()).toMillis());

            assertThat(book.bids()).hasSize(20);
            assertThat(book.asks()).hasSize(20);
            assertThat(book.symbol()).isEqualTo(BTC);
        } finally {
            stream.destroy();
        }
    }

    /**
     * REST 로 물러설 곳을 막아 둔다.
     *
     * <p>스트림이 실제로 쓰였다는 것을 이렇게 증명한다 — 물러섰다면 닿지 않는 주소에서
     * 실패한다. 호출 횟수를 세는 장치를 두면 그 장치가 곧 검사되지 않은 코드가 된다.
     */
    private static BinanceOrderBookAdapter REST_가_막힌_어댑터(
            Clock clock, StreamedOrderBook streamed) {
        return new BinanceOrderBookAdapter(
                RestClient.builder().baseUrl("http://localhost:1").build(), clock, streamed);
    }

    private static void 흐를_때까지_기다린다(StreamedOrderBook streamed) throws InterruptedException {
        Instant 포기시각 = Instant.now().plus(PATIENCE);
        while (!streamed.isFlowing() && Instant.now().isBefore(포기시각)) {
            Thread.sleep(100);
        }
        assertThat(streamed.isFlowing())
                .as("%s 안에 호가가 한 건도 오지 않았다. 구독이 받아들여져도 값이 안 올 수 있다.", PATIENCE)
                .isTrue();
    }
}
