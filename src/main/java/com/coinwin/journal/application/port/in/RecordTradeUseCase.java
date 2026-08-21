package com.coinwin.journal.application.port.in;

import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.ExecutedEntries;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeId;
import com.coinwin.position.domain.PositionPlan;

/**
 * 거래를 기록한다. 메서드 셋이 곧 생애주기 셋이다.
 *
 * <p>{@code save(Trade)} 하나로 열어 두지 않은 이유는 <b>전이만 허용하고 조작은 막기</b>
 * 위해서다. 임의의 {@code Trade} 를 받으면 이미 청산된 거래를 다시 열린 상태로 덮어쓰는
 * 요청을 거절할 근거가 없다.
 */
public interface RecordTradeUseCase {

    /** 진입 전에 계획을 남긴다. 계획 시각은 서비스의 시계가 찍는다. */
    PlannedTrade planTrade(PositionPlan plan);

    /**
     * 체결됐다. 진입 시점의 시장 상태를 함께 받는다.
     *
     * @throws com.coinwin.journal.domain.TradeNotFoundException 그런 거래가 없을 때
     * @throws com.coinwin.journal.domain.InvalidTradeException 이미 체결됐거나 닫힌 거래일 때
     */
    OpenTrade recordFills(TradeId id, ExecutedEntries entries, MarketContext context);

    /**
     * 포지션을 닫는다.
     *
     * @throws com.coinwin.journal.domain.TradeNotFoundException 그런 거래가 없을 때
     * @throws com.coinwin.journal.domain.InvalidTradeException 열려 있지 않은 거래일 때
     */
    ClosedTrade closeTrade(TradeId id, TradeClosure closure);
}
