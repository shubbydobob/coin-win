package com.coinwin.journal.application.port.in;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.Trade;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.journal.domain.TradeQuery;
import java.util.List;

/** 기록을 되묻는다. */
public interface QueryJournalUseCase {

    /**
     * @throws com.coinwin.journal.domain.TradeNotFoundException 그런 거래가 없을 때
     */
    Trade trade(TradeId id);

    List<ClosedTrade> closedTrades(TradeQuery query);

    /** 아직 닫히지 않은 것들 — 세워 둔 계획과 열려 있는 포지션. */
    List<Trade> activeTrades();

    /**
     * 조건에 드는 거래들의 집계.
     *
     * <p>{@link #closedTrades} 를 부른 뒤 부르는 쪽이 집계하게 두지 않는 이유는, 그러면
     * 집계 대상 필터를 화면마다 다시 쓰게 되기 때문이다. 조건과 집계는 함께 다녀야 한다.
     */
    JournalSummary summarize(TradeQuery query);
}
