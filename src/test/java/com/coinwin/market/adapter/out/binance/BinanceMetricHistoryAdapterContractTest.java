package com.coinwin.market.adapter.out.binance;

import com.coinwin.market.application.port.out.LoadMetricHistoryPort;
import com.coinwin.market.application.port.out.MetricHistoryPortContract;
import org.junit.jupiter.api.Tag;
import org.springframework.web.client.RestClient;

/** 바이낸스 이력 어댑터가 인메모리 어댑터와 같은 계약을 지키는가. 실제 거래소를 때린다. */
@Tag("crosscheck")
class BinanceMetricHistoryAdapterContractTest extends MetricHistoryPortContract {

    @Override
    protected LoadMetricHistoryPort port() {
        return new BinanceMetricHistoryAdapter(
                RestClient.builder().baseUrl("https://fapi.binance.com").build());
    }
}
