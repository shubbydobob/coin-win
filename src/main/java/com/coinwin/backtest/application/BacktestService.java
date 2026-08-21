package com.coinwin.backtest.application;

import com.coinwin.backtest.domain.BacktestComparison;
import com.coinwin.backtest.domain.BacktestEngine;
import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.CostModel;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.StoredCandles;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.position.domain.MaintenanceMarginPolicy;
import org.springframework.stereotype.Service;

/**
 * 캔들을 읽어 엔진에 넘긴다. 조율만 하고 계산은 하지 않는다.
 *
 * <p><b>{@code @StoredCandles} 를 쓰는 것이 완료 조건과 직결된다.</b> 거래소 어댑터로 읽으면
 * 같은 질의가 같은 답을 내지 않고, "동일 파라미터 재실행 시 결과 완전 동일" 이 그 자리에서
 * 무너진다. 수집은 {@code SyncMarketDataUseCase} 의 일이고 백테스트는 이미 저장된 것만 본다.
 *
 * <p>어댑터가 아니라 <b>포트</b>를 받는다 — ArchUnit 규칙 5 가 그것을 강제하고, 완료 조건
 * "backtest 에 바이낸스 관련 코드가 한 줄도 없다" 가 여기서 지켜진다.
 */
@Service
public class BacktestService {

    private final LoadCandlesPort storedCandles;
    private final BacktestEngine engine;

    public BacktestService(
            @StoredCandles LoadCandlesPort storedCandles, MaintenanceMarginPolicy policy) {
        this.storedCandles = storedCandles;
        this.engine = new BacktestEngine(policy);
    }

    public BacktestResult run(BacktestSpec spec) {
        return engine.run(spec, candlesFor(spec));
    }

    /**
     * 지표 필터를 끈 실행과 켠 실행.
     *
     * <p>캔들을 한 번만 읽어 두 실행에 같은 입력을 준다. 두 번 읽으면 그 사이에 수집이 돌아
     * 입력이 달라질 수 있고, 그러면 비교가 필터 때문인지 데이터 때문인지 가려지지 않는다.
     */
    public BacktestComparison compareIndicatorFilter(BacktestSpec spec) {
        CandleSeries series = candlesFor(spec);
        return new BacktestComparison(
                engine.run(spec.withIndicatorFilter(false), series),
                engine.run(spec.withIndicatorFilter(true), series));
    }

    /** 비용을 뺀 실행과 넣은 실행. 수수료가 엣지를 먹어 치우는지를 수치로 낸다. */
    public BacktestComparison compareCosts(BacktestSpec spec) {
        CandleSeries series = candlesFor(spec);
        return new BacktestComparison(
                engine.run(spec.withCosts(CostModel.free()), series),
                engine.run(spec, series));
    }

    private CandleSeries candlesFor(BacktestSpec spec) {
        return storedCandles.load(spec.query());
    }
}
