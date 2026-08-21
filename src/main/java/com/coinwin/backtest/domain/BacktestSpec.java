package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.CandleQuery;

/**
 * 백테스트 한 번을 완전히 결정하는 입력.
 *
 * <p>완료 조건("동일 파라미터 재실행 시 결과 완전 동일")이 이 타입에 걸려 있다. 여기 없는
 * 것이 결과를 바꾸면 그 실행은 재현할 수 없다 — 그래서 시각도 난수도 쓰지 않는다.
 *
 * <p>{@link CandleQuery} 를 들고 있으므로 <b>같은 스펙은 같은 캔들을 가리킨다.</b> 조회가
 * 거래소를 때리지 않고 저장된 것만 읽는 것({@code LoadMarketDataUseCase})이 여기서 값을 한다.
 */
public record BacktestSpec(
        CandleQuery query, StrategySettings strategy, AccountSettings account, CostModel costs) {

    public BacktestSpec {
        DomainValues.required(query, "캔들 조회 조건");
        DomainValues.required(strategy, "전략 설정");
        DomainValues.required(account, "계좌 설정");
        DomainValues.required(costs, "비용 모델");
    }

    /** 지표 필터만 뒤집은 스펙. 온오프 비교는 이 하나가 유일한 차이여야 성립한다. */
    public BacktestSpec withIndicatorFilter(boolean enabled) {
        EntryRules rules = strategy.rules();
        return new BacktestSpec(query, new StrategySettings(strategy.zones(),
                new EntryRules(rules.stopBufferMultiple(), rules.minRiskReward(), enabled)),
                account, costs);
    }

    /** 비용만 바꾼 스펙. 수수료가 엣지를 먹어 치우는지 보려면 같은 스펙을 두 번 돌린다. */
    public BacktestSpec withCosts(CostModel replacement) {
        DomainValues.required(replacement, "비용 모델");
        return new BacktestSpec(query, strategy, account, replacement);
    }

    /** 잔고 모드만 바꾼 스펙. */
    public BacktestSpec withCapitalMode(CapitalMode mode) {
        DomainValues.required(mode, "잔고 모드");
        return new BacktestSpec(query, strategy, new AccountSettings(account.initialCapital(),
                account.riskPercent(), account.leverage(), mode), costs);
    }
}
