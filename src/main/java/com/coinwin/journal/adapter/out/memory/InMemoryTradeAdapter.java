package com.coinwin.journal.adapter.out.memory;

import com.coinwin.journal.application.port.out.LoadTradesPort;
import com.coinwin.journal.application.port.out.SaveTradePort;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 거래를 메모리에 담는 어댑터.
 *
 * <p>존재 이유는 {@code market} 의 인메모리 어댑터와 같다 — <b>DB 없이 애플리케이션 서비스
 * 테스트를 전부 돌리기 위해서다.</b> Phase 5 완료 조건이 그것을 직접 요구한다.
 *
 * <p>Spring 애너테이션이 없다. 애플리케이션 기본 구성은 영속화 어댑터를 쓰고 이 어댑터는
 * 테스트가 {@code new} 로 만든다. 두 구현이 컨텍스트에 함께 올라가 어느 쪽이 주입될지
 * 설정에 따라 갈리는 상황을 애초에 만들지 않는다.
 *
 * <p>필터링은 {@link TradeQuery#matches} 에 맡긴다. 여기서 조건을 다시 해석하면 JPA 어댑터와
 * 뜻이 갈릴 수 있고, 그러면 두 어댑터가 하나의 계약을 통과한다는 말이 성립하지 않는다.
 */
public class InMemoryTradeAdapter implements SaveTradePort, LoadTradesPort {

    private final Map<TradeId, Trade> store = new ConcurrentHashMap<>();

    @Override
    public void save(Trade trade) {
        store.put(trade.id(), trade);
    }

    @Override
    public Optional<Trade> findById(TradeId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ClosedTrade> findClosed(TradeQuery query) {
        return store.values().stream()
                .filter(ClosedTrade.class::isInstance)
                .map(ClosedTrade.class::cast)
                .filter(query::matches)
                .sorted(Comparator.comparing(ClosedTrade::openedAt))
                .toList();
    }

    @Override
    public List<Trade> findActive() {
        return store.values().stream()
                .filter(trade -> !(trade instanceof ClosedTrade))
                .toList();
    }
}
