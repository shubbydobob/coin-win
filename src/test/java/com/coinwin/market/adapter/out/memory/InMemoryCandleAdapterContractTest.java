package com.coinwin.market.adapter.out.memory;

import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPortContract;
import org.junit.jupiter.api.BeforeEach;

/**
 * 인메모리 어댑터가 조회·저장 계약을 모두 통과하는지.
 *
 * <p>스위트를 물려받기만 하고 테스트를 하나도 새로 쓰지 않는 것이 정상이다. 여기에 이
 * 어댑터만의 테스트를 덧붙이고 싶어진다면, 그 성질이 계약에 없어야 할 이유가 있는지 먼저
 * 따져야 한다.
 */
class InMemoryCandleAdapterContractTest extends SaveCandlesPortContract {

    private InMemoryCandleAdapter adapter;

    @BeforeEach
    void 어댑터를_새로_만든다() {
        adapter = new InMemoryCandleAdapter();
    }

    @Override
    protected LoadCandlesPort loadPort() {
        return adapter;
    }

    @Override
    protected SaveCandlesPort savePort() {
        return adapter;
    }
}
