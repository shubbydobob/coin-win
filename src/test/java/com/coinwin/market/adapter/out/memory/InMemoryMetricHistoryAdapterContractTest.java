package com.coinwin.market.adapter.out.memory;

import com.coinwin.market.application.port.out.LoadMetricHistoryPort;
import com.coinwin.market.application.port.out.MetricHistoryPortContract;

/** 인메모리 어댑터가 지표 이력 포트의 계약을 지키는가. */
class InMemoryMetricHistoryAdapterContractTest extends MetricHistoryPortContract {

    @Override
    protected LoadMetricHistoryPort port() {
        return InMemoryMetricHistoryAdapter.withSample(SYMBOL);
    }
}
