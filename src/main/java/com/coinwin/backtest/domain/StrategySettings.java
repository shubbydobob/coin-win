package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 전략 쪽 설정 전부. {@link BacktestSpec} 의 파라미터 수를 넷 안에 두려고 묶었지만, 묶음 자체가
 * 뜻을 갖는다 — <b>계좌를 바꿔도 이쪽이 같으면 같은 신호가 나온다.</b>
 */
public record StrategySettings(ZoneSettings zones, EntryRules rules) {

    public StrategySettings {
        DomainValues.required(zones, "대 설정");
        DomainValues.required(rules, "진입 규칙");
    }
}
