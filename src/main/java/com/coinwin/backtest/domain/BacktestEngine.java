package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.position.domain.MaintenanceMarginPolicy;

/**
 * 백테스트 실행 진입점.
 *
 * <p><b>캔들을 스스로 가져오지 않는다.</b> 이미 읽어 온 묶음을 받는다 — 포트를 소비하는 것은
 * {@code backtest/application} 의 일이고, 그래야 도메인이 결정론적인 순수 함수로 남는다.
 * 같은 스펙과 같은 캔들이면 언제 돌려도 같은 결과가 나온다.
 *
 * <p>{@link MaintenanceMarginPolicy} 는 주입받는다. 도메인 테스트는 고정 MMR 로 돌리고,
 * 실사용은 거래소 구간표 기반 구현이 들어온다 ({@code docs/adr/008}).
 */
public record BacktestEngine(MaintenanceMarginPolicy policy) {

    public BacktestEngine {
        DomainValues.required(policy, "유지증거금 정책");
    }

    public BacktestResult run(BacktestSpec spec, CandleSeries series) {
        DomainValues.required(spec, "백테스트 스펙");
        DomainValues.required(series, "캔들 묶음");
        return new BacktestRun(spec, policy, series).execute();
    }
}
