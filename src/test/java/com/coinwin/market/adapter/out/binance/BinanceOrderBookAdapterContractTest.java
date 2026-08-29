package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.application.port.out.OrderBookPortContract;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.RestClient;

/**
 * 바이낸스 어댑터가 <b>인메모리 어댑터와 같은 계약</b>을 지키는가.
 *
 * <p>실제 거래소를 때리므로 {@code crosscheck} 태그로 분리한다. {@code check} 는 네트워크 없이
 * 돌아야 한다 — Phase 7 의 {@code liveAi} 와 같은 자리다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다. 검사 대상은 어댑터와 매핑이지 애플리케이션 조립이 아니다.
 *
 * <p><b>스트림을 물리지 않는다.</b> 빈 {@code StreamedOrderBook} 을 주면 어댑터가 언제나 REST
 * 로 간다 — 여기서 확인하려는 것이 <b>거래소 응답의 매핑</b>이기 때문이다. 스트림이 켜져
 * 있으면 이 스위트는 그 경로를 한 번도 지나지 않고, 그러면 REST 매핑이 깨져도 초록이 된다.
 * 스트림 쪽 경로는 {@code BinanceOrderBookAdapterTest} 가 따로 본다.
 */
@Tag("crosscheck")
class BinanceOrderBookAdapterContractTest extends OrderBookPortContract {

    @Override
    protected LoadOrderBookPort port() {
        return new BinanceOrderBookAdapter(
                RestClient.builder().baseUrl("https://fapi.binance.com").build(),
                Clock.systemUTC(),
                new StreamedOrderBook(Clock.systemUTC(), new BinanceStreamProperties(
                        "wss://localhost:1/ws", "BTCUSDT", 20,
                        Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(10))));
    }
}
