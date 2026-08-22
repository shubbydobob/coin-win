package com.coinwin.account.adapter.out.memory;

import com.coinwin.account.application.port.out.LoadExchangePositionsPort;
import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.market.domain.Symbol;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 키 없이 도는 거래소 포지션 어댑터.
 *
 * <p><b>이것이 있어야 서비스 테스트가 키 없이 돈다.</b> {@code journal} 의 인메모리 어댑터가
 * "DB 없이 도메인 테스트를 돌리기 위해" 존재하는 것과 같은 자리다.
 *
 * <p>운영 컨텍스트에는 올라오지 않는다 — 빈으로 만들지 않고 테스트가 직접 생성한다. 폴백으로
 * 두면 키가 없을 때 <b>빈 포지션이 사실인 것처럼</b> 화면에 뜨고, 그것은 "거래소에 아무것도
 * 없다" 는 거짓말이 된다. 키가 없으면 그 자리는 <b>비활성</b>이어야지 비어 있으면 안 된다.
 */
public class InMemoryExchangePositionAdapter implements LoadExchangePositionsPort {

    private final List<ExchangePosition> positions = new CopyOnWriteArrayList<>();

    public InMemoryExchangePositionAdapter(ExchangePosition... seed) {
        this.positions.addAll(List.of(seed));
    }

    @Override
    public List<ExchangePosition> positionsFor(Symbol symbol) {
        return positions.stream().filter(position -> position.symbol().equals(symbol)).toList();
    }

    /** 테스트가 도중에 거래소 상태를 바꾸고 싶을 때. */
    public void replaceWith(Collection<ExchangePosition> replacement) {
        List<ExchangePosition> copy = new ArrayList<>(replacement);
        positions.clear();
        positions.addAll(copy);
    }
}
