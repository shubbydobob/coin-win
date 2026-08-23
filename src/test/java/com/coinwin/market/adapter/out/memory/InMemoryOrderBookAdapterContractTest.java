package com.coinwin.market.adapter.out.memory;

import com.coinwin.market.application.port.out.LoadOrderBookPort;
import com.coinwin.market.application.port.out.OrderBookPortContract;
import java.time.Instant;

/** 인메모리 어댑터가 호가 포트의 계약을 지키는가. 바이낸스 어댑터도 같은 스위트를 돈다. */
class InMemoryOrderBookAdapterContractTest extends OrderBookPortContract {

    private static final Instant AT = Instant.parse("2026-08-23T09:00:00Z");

    @Override
    protected LoadOrderBookPort port() {
        return InMemoryOrderBookAdapter.withSample(SYMBOL, AT);
    }
}
