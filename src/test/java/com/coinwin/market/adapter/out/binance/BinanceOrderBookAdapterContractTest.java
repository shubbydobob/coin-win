package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.application.port.out.OrderBookPortContract;
import java.time.Clock;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.RestClient;

/**
 * 바이낸스 어댑터가 <b>인메모리 어댑터와 같은 계약</b>을 지키는가.
 *
 * <p>실제 거래소를 때리므로 {@code crosscheck} 태그로 분리한다. {@code check} 는 네트워크 없이
 * 돌아야 한다 — Phase 7 의 {@code liveAi} 와 같은 자리다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다. 검사 대상은 어댑터와 매핑이지 애플리케이션 조립이 아니다.
 */
@Tag("crosscheck")
class BinanceOrderBookAdapterContractTest extends OrderBookPortContract {

    @Override
    protected LoadOrderBookPort port() {
        return new BinanceOrderBookAdapter(
                RestClient.builder().baseUrl("https://fapi.binance.com").build(),
                Clock.systemUTC());
    }
}
