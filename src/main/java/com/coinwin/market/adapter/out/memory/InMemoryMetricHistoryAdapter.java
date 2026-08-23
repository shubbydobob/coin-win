package com.coinwin.market.adapter.out.memory;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.out.LoadMetricHistoryPort;
import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.Symbol;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * 지표 이력을 메모리에 담는 어댑터. 거래소 없이 이상치 계산을 검증하기 위해 있다.
 *
 * <p><b>기본 표본에 동점을 일부러 섞는다.</b> 실제 펀딩비는 연속으로 같은 값이 나오고, 그것이
 * {@code Percentile} 이 중간순위를 쓰는 이유다. 표본이 전부 다른 값이면 그 규칙이 테스트를
 * 지나가면서도 검증되지 않는다.
 */
public class InMemoryMetricHistoryAdapter implements LoadMetricHistoryPort {

    private final Map<Symbol, MetricHistory> funding = new ConcurrentHashMap<>();
    private final Map<Symbol, MetricHistory> openInterest = new ConcurrentHashMap<>();
    private final Map<Symbol, MetricHistory> longShort = new ConcurrentHashMap<>();

    public static InMemoryMetricHistoryAdapter withSample(Symbol symbol) {
        InMemoryMetricHistoryAdapter adapter = new InMemoryMetricHistoryAdapter();
        // 절반이 같은 값이다 — 동점 처리가 실제로 걸리는 표본이다.
        adapter.funding.put(symbol, series(90, index -> index % 2 == 0 ? "0.0001" : "0.000" + (index % 9 + 1)));
        adapter.openInterest.put(symbol, series(30, index -> String.valueOf(107000 + index)));
        adapter.longShort.put(symbol, series(30, index -> "0.9" + (index % 10)));
        return adapter;
    }

    public void putFunding(Symbol symbol, MetricHistory history) {
        funding.put(symbol, history);
    }

    @Override
    public MetricHistory fundingRates(Symbol symbol, int limit) {
        return limited(funding, symbol, limit, "펀딩비 이력");
    }

    @Override
    public MetricHistory openInterest(Symbol symbol, int limit) {
        return limited(openInterest, symbol, limit, "미결제약정 이력");
    }

    @Override
    public MetricHistory longShortRatios(Symbol symbol, int limit) {
        return limited(longShort, symbol, limit, "롱숏비율 이력");
    }

    /** <b>뒤에서부터</b> 자른다. 이력에서 필요한 것은 언제나 최근이다. */
    private static MetricHistory limited(
            Map<Symbol, MetricHistory> source, Symbol symbol, int limit, String label) {
        MetricHistory history = source.get(symbol);
        if (history == null) {
            throw new ExternalDataUnavailableException(label + "이(가) 없다: " + symbol.value());
        }
        List<BigDecimal> samples = history.samples();
        return new MetricHistory(samples.subList(Math.max(0, samples.size() - limit), samples.size()));
    }

    private static MetricHistory series(int count, IntFunction<String> value) {
        return new MetricHistory(
                IntStream.range(0, count).mapToObj(value).map(BigDecimal::new).toList());
    }
}
