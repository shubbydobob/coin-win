package com.coinwin.market.adapter.out.memory;

import com.coinwin.common.domain.ExchangeRate;
import com.coinwin.market.application.port.out.LoadExchangeRatePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * 환율을 메모리에 담는 어댑터. 거래소 없이 화면·컨트롤러 테스트를 돌리기 위해 있다.
 *
 * <p><b>"환율을 얻지 못한 상태" 도 만들 수 있어야 한다.</b> 그 경로가 응답에서 원화를 통째로
 * 비우는 자리이고, 검증되지 않으면 업비트가 막히는 날 처음 실행된다.
 */
public final class InMemoryExchangeRateAdapter implements LoadExchangeRatePort {

    private final ExchangeRate rate;

    private InMemoryExchangeRateAdapter(ExchangeRate rate) {
        this.rate = rate;
    }

    public static InMemoryExchangeRateAdapter at(String wonPerUsdt, Instant observedAt) {
        return new InMemoryExchangeRateAdapter(
                new ExchangeRate(new BigDecimal(wonPerUsdt), observedAt));
    }

    /** 거래소에 닿지 못한 상태. 0 원이 아니라 <b>없음</b>이다. */
    public static InMemoryExchangeRateAdapter unavailable() {
        return new InMemoryExchangeRateAdapter(null);
    }

    @Override
    public Optional<ExchangeRate> wonPerUsdt() {
        return Optional.ofNullable(rate);
    }
}
