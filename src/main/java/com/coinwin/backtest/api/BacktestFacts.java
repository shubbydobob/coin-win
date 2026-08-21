package com.coinwin.backtest.api;

import com.coinwin.ai.domain.SummaryFacts;
import com.coinwin.backtest.domain.AccountSettings;
import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.backtest.domain.BacktestSpec;
import com.coinwin.backtest.domain.EntryRules;
import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.market.domain.CandleQuery;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 백테스트 결과에서 <b>요약이 써도 되는 수</b>를 고른다.
 *
 * <p>여기 넣지 않은 수는 요약에 나올 수 없다 — 나오면 {@code Narrative} 가 거절한다. 그래서
 * 이 목록은 "모델에게 무엇을 보여 줄까" 가 아니라 <b>"무엇을 말해도 되는가"</b> 를 정하는 자리다.
 *
 * <p>거래 목록과 자산 곡선은 넣지 않는다. 개별 거래를 언급하는 요약은 유용하지 않고, 값을
 * 통째로 넘기면 대조해야 할 수만 수백 개로 늘어난다.
 */
final class BacktestFacts {

    private BacktestFacts() {
    }

    static SummaryFacts of(BacktestResult result) {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.putAll(outcome(result));
        numbers.putAll(settings(result.spec()));
        return new SummaryFacts(numbers, context(result.spec()));
    }

    private static Map<String, BigDecimal> outcome(BacktestResult result) {
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("거래 수", BigDecimal.valueOf(result.totalTrades()));
        numbers.put("승률(%)", result.winRate().value());
        result.profitFactor().ifPresent(factor ->
                numbers.put("손익비", factor.setScale(2, java.math.RoundingMode.HALF_UP)));
        numbers.put("순손익(USDT)", result.netPnl().value());
        numbers.put("최종 자산(USDT)", result.finalEquity().value());
        numbers.put("최대낙폭(%)", result.maxDrawdown().value());
        return numbers;
    }

    private static Map<String, BigDecimal> settings(BacktestSpec spec) {
        ZoneSettings zones = spec.strategy().zones();
        EntryRules rules = spec.strategy().rules();
        AccountSettings account = spec.account();
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        numbers.put("초기 자본(USDT)", account.initialCapital().value());
        numbers.put("거래당 리스크(%)", account.riskPercent().value());
        numbers.put("레버리지", BigDecimal.valueOf(account.leverage()));
        numbers.put("피벗 탐지 폭(봉)", BigDecimal.valueOf(zones.pivotLookback()));
        numbers.put("군집 배수(ATR)", zones.clusterMultiple());
        numbers.put("최소 터치 횟수", BigDecimal.valueOf(zones.minTouches()));
        numbers.put("ATR 기간", BigDecimal.valueOf(zones.atrPeriod()));
        numbers.put("손절 버퍼(ATR)", rules.stopBufferMultiple());
        numbers.put("최소 손익비 기준", rules.minRiskReward());
        return numbers;
    }

    private static Map<String, String> context(BacktestSpec spec) {
        CandleQuery query = spec.query();
        Map<String, String> context = new LinkedHashMap<>();
        context.put("종목", query.symbol().value());
        context.put("주기", query.interval().code());
        context.put("구간", "%s ~ %s".formatted(query.range().from(), query.range().to()));
        context.put("잔고 모드", spec.account().capitalMode().name());
        context.put("지표 필터", spec.strategy().rules().indicatorFilter() ? "켬" : "끔");
        return context;
    }
}
