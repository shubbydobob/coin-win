package com.coinwin.trading.domain;

/**
 * 아무것도 하지 않는다. <b>기본 전략이다.</b>
 *
 * <p>비어 있는 자리를 메우는 임시 구현이 아니라 <b>옳은 기본값</b>이다. 이 저장소가 두 번
 * 쟀고 두 번 다 진입 규칙이 없다고 나왔다({@code docs/adr/021} · {@code docs/adr/022}).
 * 규칙이 없을 때 봇이 해야 할 일은 추측하는 것이 아니라 가만히 있는 것이다.
 *
 * <p>그래도 루프는 돈다 — 읽고, 판단하고(아무것도 안 하기로), 기록한다. <b>그 기록이 쌓이는
 * 것 자체가 값을 한다</b>: 루프가 살아 있는지, 시장을 제대로 읽는지, 사이클이 겹치지 않는지가
 * 전략을 꽂기 전에 증명된다.
 */
public final class HoldStrategy implements TradingStrategy {

    @Override
    public String name() {
        return "가만히 있기";
    }

    @Override
    public CycleDecision decide(BotContext now) {
        return CycleDecision.none();
    }
}
