package com.coinwin.trading.application.port.in;

import com.coinwin.trading.domain.TradingCycle;

/** 봇을 한 번 깨운다. */
public interface RunTradingCycleUseCase {

    /**
     * 읽고 · 판단하고 · 안전장치를 통과시키고 · 내고 · 기록한다.
     *
     * <p><b>던지지 않는다.</b> 자동으로 도는 루프에서 예외가 올라가면 다음 사이클이 돌지
     * 안 돌지가 스케줄러의 사정이 된다. 실패는 {@link TradingCycle} 에 이유로 담겨 내려오고,
     * 그러면 <b>실패한 사이클도 기록에 남는다.</b>
     */
    TradingCycle runOnce();
}
