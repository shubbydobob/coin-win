package com.coinwin.market.adapter.out.memory;

import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.SaveCandlesPort;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 캔들을 메모리에 담는 어댑터.
 *
 * <p>존재 이유는 <b>DB 없이 도메인·서비스 테스트를 돌리기 위해서</b>다. 근거는
 * {@code .claude/docs/architecture.md} — 이 어댑터가 있기 때문에 {@code market} 을
 * 포트·어댑터로 만들 근거가 생긴다("구현체가 둘 이상 존재").
 *
 * <p>Spring 애너테이션이 없다. 애플리케이션 기본 구성은 영속화 어댑터를 쓰고, 이 어댑터는
 * 테스트가 직접 {@code new} 로 만든다. 컨텍스트에 두 구현이 함께 올라가 어느 쪽이 주입될지
 * 설정에 따라 갈리는 상황을 애초에 만들지 않는다.
 *
 * <p>시각별 정렬은 {@link ConcurrentSkipListMap} 이 맡는다. 조회할 때마다 정렬하면 계약
 * 테스트의 "시간 오름차순" 이 자료구조가 아니라 조회 코드에 의존하게 된다.
 */
public class InMemoryCandleAdapter implements LoadCandlesPort, SaveCandlesPort {

    private final Map<SeriesKey, NavigableMap<Instant, Candle>> store = new ConcurrentHashMap<>();

    @Override
    public CandleSeries load(CandleQuery query) {
        NavigableMap<Instant, Candle> byOpenTime = store.get(
                new SeriesKey(query.symbol(), query.interval()));
        if (byOpenTime == null) {
            return CandleSeries.empty();
        }
        TimeRange range = query.range();
        return new CandleSeries(
                byOpenTime.subMap(range.from(), true, range.to(), false).values().stream().toList());
    }

    /**
     * {@inheritDoc}
     *
     * <p>새로 저장된 수는 <b>맵 크기의 증가분</b>이다. 이미 있던 시각에 {@code put} 하면
     * 값만 바뀌고 크기는 그대로이므로, 갱신과 신규가 자연스럽게 갈린다.
     */
    @Override
    public int save(Symbol symbol, CandleInterval interval, CandleSeries candles) {
        NavigableMap<Instant, Candle> byOpenTime = store.computeIfAbsent(
                new SeriesKey(symbol, interval), key -> new ConcurrentSkipListMap<>());
        int before = byOpenTime.size();
        candles.candles().forEach(candle -> byOpenTime.put(candle.openTime(), candle));
        return byOpenTime.size() - before;
    }

    private record SeriesKey(Symbol symbol, CandleInterval interval) {
    }
}
