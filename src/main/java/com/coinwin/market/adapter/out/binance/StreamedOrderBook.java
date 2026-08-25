package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.Symbol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 스트림으로 밀려온 가장 최근 호가 하나.
 *
 * <p><b>낡은 값을 내주지 않는 것이 이 타입의 전부다.</b> 스트림은 소리 없이 멎을 수 있고 —
 * 실제로 선물 {@code @ticker} 스트림이 구독을 받아 주면서 한 건도 보내지 않는 것을
 * 확인했다(2026-08-25) — 그때 마지막 호가를 계속 내주면 화면은 <b>몇 분 전 호가를 지금으로
 * 읽는다.</b> 그것은 REST 로 3초마다 묻던 것보다 나쁘다.
 *
 * <p><b>신선함은 우리 시계로만 잰다.</b> 받은 시각과 지금을 같은 시계로 재므로 기계가
 * 거래소보다 앞서 있든 뒤처져 있든 그 차이가 상쇄된다. 화면에 적히는 관측 시각은 거래소가 준
 * 값 그대로이고, 그 둘을 섞어서 빼면 33초 어긋난 기계에서 모든 호가가 낡은 것이 된다.
 *
 * <p>스트림이 꺼져 있으면 이 타입은 그냥 늘 비어 있다. 부르는 쪽은 REST 로 가면 된다.
 */
@Component
class StreamedOrderBook {

    private final Clock clock;

    private final Duration freshness;

    private volatile Streamed latest;

    private record Streamed(OrderBook book, Instant receivedAt) {
    }

    StreamedOrderBook(Clock clock, BinanceStreamProperties properties) {
        this.clock = clock;
        this.freshness = properties.freshness();
    }

    void push(OrderBook book) {
        latest = new Streamed(book, clock.instant());
    }

    /** 지금 흐르고 있는가. 멎었으면 다시 이어야 한다는 뜻이다. */
    boolean isFlowing() {
        return isFresh(latest);
    }

    /**
     * 이 요청을 스트림이 담당할 수 있으면 그 호가를, 아니면 빈 값을.
     *
     * <p>담당하지 못하는 경우가 셋이다 — 다른 종목, 스트림보다 깊은 요청, 그리고 낡음.
     * <b>셋 다 오류가 아니다.</b> 부르는 쪽이 REST 로 물러서면 되고, 그 물러섬이 이 기능의
     * 안전장치 전부다.
     */
    Optional<OrderBook> fresh(Symbol symbol, OrderBookDepth depth) {
        Streamed streamed = latest;
        if (streamed == null || !isFresh(streamed) || !streamed.book().symbol().equals(symbol)) {
            return Optional.empty();
        }
        return covers(streamed.book(), depth)
                ? Optional.of(streamed.book().truncatedTo(depth))
                : Optional.empty();
    }

    private boolean isFresh(Streamed streamed) {
        return streamed != null
                && Duration.between(streamed.receivedAt(), clock.instant()).compareTo(freshness) <= 0;
    }

    /** 스트림이 20단을 받는데 25단을 물으면 자를 것이 없다. 채워 넣지 않고 물러선다. */
    private static boolean covers(OrderBook book, OrderBookDepth depth) {
        return book.bids().size() >= depth.levels() && book.asks().size() >= depth.levels();
    }
}
