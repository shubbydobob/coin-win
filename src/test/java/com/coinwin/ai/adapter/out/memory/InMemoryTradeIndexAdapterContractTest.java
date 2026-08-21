package com.coinwin.ai.adapter.out.memory;

import com.coinwin.ai.application.port.out.IndexTradesPort;
import com.coinwin.ai.application.port.out.SearchTradesPort;
import com.coinwin.ai.application.port.out.TradeIndexContract;

/** 인메모리 어댑터가 계약을 지키는지. DB 도 임베딩도 없이 돈다. */
class InMemoryTradeIndexAdapterContractTest extends TradeIndexContract {

    private final InMemoryTradeIndexAdapter adapter = new InMemoryTradeIndexAdapter();

    @Override
    protected IndexTradesPort indexPort() {
        return adapter;
    }

    @Override
    protected SearchTradesPort searchPort() {
        return adapter;
    }
}
