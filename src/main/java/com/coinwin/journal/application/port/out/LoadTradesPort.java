package com.coinwin.journal.application.port.out;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import java.util.List;
import java.util.Optional;

/**
 * 거래를 읽는다.
 *
 * <p>{@link #findClosed} 만 {@link ClosedTrade} 로 좁혀 돌려준다. 집계에 넣을 수 있는 것은
 * 끝난 거래뿐이고, 그 필터를 여기서 걸어 두면 부르는 쪽이 빠뜨릴 수 없다. 반환 타입이
 * {@code List<Trade>} 였다면 집계마다 캐스팅과 {@code instanceof} 가 붙었을 것이다.
 */
public interface LoadTradesPort {

    Optional<Trade> findById(TradeId id);

    /**
     * 조건에 드는 끝난 거래. <b>진입 시각 오름차순</b>이다.
     *
     * <p>순서를 계약에 넣는 이유는 거래 간격 때문이다. 정렬을 부르는 쪽에 맡기면 어댑터마다
     * 다른 순서가 나오고, 그러면 같은 질의의 집계가 어댑터에 따라 갈릴 수 있다.
     */
    List<ClosedTrade> findClosed(TradeQuery query);

    /**
     * 아직 닫히지 않은 거래 — 세워만 둔 계획과 열려 있는 포지션.
     *
     * <p>1인 사용자·단일 포지션 전제라 보통 0건이거나 1건이다. 그래도 목록인 이유는
     * 계획을 여럿 세워 두고 그중 하나만 체결되는 경우가 정상이기 때문이다.
     */
    List<Trade> findActive();
}
