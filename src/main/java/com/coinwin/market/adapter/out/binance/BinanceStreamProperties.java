package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.domain.OrderBookDepth;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 호가 스트림 접속 설정.
 *
 * <p><b>스트림은 한 종목·한 깊이뿐이다.</b> 화면이 보는 것이 하나이고, 여러 종목을 물면 쓰지
 * 않는 값이 계속 밀려온다. 어댑터는 여전히 어떤 종목이든 받으며, 스트림이 담당하지 않는
 * 요청은 REST 로 간다.
 *
 * <p>{@code enabled} 가 여기 없는 이유는 그 키가 <b>빈을 만들지 말지</b>를 정하기 때문이다
 * ({@code BinanceDepthStream} 의 {@code @ConditionalOnProperty}). 값으로 받아 두면 두 곳에서
 * 같은 것을 판단하게 된다.
 *
 * @param url 스트림 주소. 뒤에 종목과 깊이가 붙는다
 * @param symbol 어느 종목을 물 것인가
 * @param depth 몇 단을 받을 것인가. {@link OrderBookDepth} 가 허용하는 값이어야 하고, 아니면
 *     기동 시점에 실패한다 — 거래소가 거절하는 것을 첫 요청까지 기다려 알 이유가 없다
 * @param connectTimeout 손잡기 제한 시간. 기본값이 <b>무한</b>이라 명시한다 —
 *     {@code BinanceClientConfig} 가 REST 에 같은 이유로 두고 있다
 * @param freshness 이보다 오래된 호가는 스트림이 살아 있다고 보지 않는다
 * @param reconnectDelay 흐름이 멎었는지 확인하는 주기. 멎었으면 그 자리에서 다시 잇는다
 */
@ConfigurationProperties("coinwin.market.binance.stream")
public record BinanceStreamProperties(
        String url,
        String symbol,
        int depth,
        Duration connectTimeout,
        Duration freshness,
        Duration reconnectDelay) {

    /**
     * 부분 호가 스트림의 갱신 주기. 거래소가 주는 가장 빠른 값이고 <b>느리게 받을 이유가
     * 없다</b> — 우리가 폴링하는 것이 아니라 받아 두는 것이라 더 자주 와도 비용이 늘지 않는다.
     */
    private static final String INTERVAL = "100ms";

    public BinanceStreamProperties {
        OrderBookDepth.of(depth);
    }

    public OrderBookDepth orderBookDepth() {
        return OrderBookDepth.of(depth);
    }

    /** {@code wss://.../btcusdt@depth20@100ms}. 스트림 이름은 소문자여야 한다. */
    public URI streamUri() {
        return URI.create("%s/%s@depth%d@%s".formatted(
                url, symbol.toLowerCase(Locale.ROOT), depth, INTERVAL));
    }
}
