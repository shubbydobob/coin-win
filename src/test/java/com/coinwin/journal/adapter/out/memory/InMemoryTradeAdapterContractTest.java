package com.coinwin.journal.adapter.out.memory;

import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.application.port.out.TradeRepositoryContract;
import org.junit.jupiter.api.BeforeEach;

/**
 * 인메모리 어댑터가 거래 저장소 계약을 지키는지. Docker 도 Spring 컨텍스트도 필요 없다.
 *
 * <p>이 스위트가 밀리초 안에 끝나기 때문에 애플리케이션 서비스 테스트를 DB 없이 돌릴 수 있다.
 * 같은 스위트를 JPA 어댑터도 통과한다는 것이 그 대체를 정당화한다.
 */
class InMemoryTradeAdapterContractTest extends TradeRepositoryContract {

    private InMemoryTradeAdapter adapter;

    @BeforeEach
    void 어댑터를_새로_만든다() {
        adapter = new InMemoryTradeAdapter();
    }

    @Override
    protected SaveTradePort savePort() {
        return adapter;
    }

    @Override
    protected LoadTradesPort loadPort() {
        return adapter;
    }
}
