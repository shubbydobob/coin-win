package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Instant;
import java.util.List;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * 한 종목·한 주기의 캔들 묶음. <b>시간 오름차순이고 같은 시각이 두 번 나타나지 않는다.</b>
 *
 * <p>이 불변식이 Phase 3 완료 조건("캔들 증분 저장에 중복 없음")을 타입 수준에서 떠받친다.
 * 중복 제거를 어댑터 세 곳에 각각 심으면 세 곳의 정의가 갈라진다. 여기서 한 번 막으면 어느
 * 어댑터가 중복을 흘리든 묶음을 만드는 순간 터진다.
 *
 * <p>생성자는 정렬하거나 중복을 걷어내지 <b>않고 거부한다.</b> 조용히 고쳐 주면 어댑터의
 * 버그가 증거를 남기지 않고 사라진다. 겹치는 구간을 이어 붙이는 것은 {@link #merge} 의 일이다.
 */
public record CandleSeries(List<Candle> candles) {

    public CandleSeries {
        DomainValues.required(candles, "캔들 묶음");
        assertAscendingAndDistinct(candles);
        candles = List.copyOf(candles);
    }

    public static CandleSeries empty() {
        return new CandleSeries(List.of());
    }

    public static CandleSeries of(Candle... candles) {
        return new CandleSeries(List.of(candles));
    }

    public int size() {
        return candles.size();
    }

    public boolean isEmpty() {
        return candles.isEmpty();
    }

    public Candle first() {
        return requireNotEmpty().getFirst();
    }

    public Candle last() {
        return requireNotEmpty().getLast();
    }

    /** 구간에 든 캔들만 남긴 묶음. 구간은 반열림이므로 끝 시각의 캔들은 빠진다. */
    public CandleSeries within(TimeRange range) {
        DomainValues.required(range, "조회 구간");
        return new CandleSeries(candles.stream().filter(c -> range.contains(c.openTime())).toList());
    }

    /**
     * 두 묶음을 시각 기준으로 합친다. 증분 저장의 도메인 표현이다.
     *
     * <p>같은 시각이 양쪽에 있으면 <b>인자로 받은 쪽이 이긴다.</b> 거래소는 아직 닫히지 않은
     * 캔들을 먼저 주고 나중에 정정해서 다시 주기 때문에, 나중에 받은 값이 더 참이다.
     */
    public CandleSeries merge(CandleSeries other) {
        DomainValues.required(other, "합칠 캔들 묶음");
        SequencedMap<Instant, Candle> byOpenTime = new TreeMap<>();
        candles.forEach(candle -> byOpenTime.put(candle.openTime(), candle));
        other.candles.forEach(candle -> byOpenTime.put(candle.openTime(), candle));
        return new CandleSeries(List.copyOf(byOpenTime.values()));
    }

    private List<Candle> requireNotEmpty() {
        if (candles.isEmpty()) {
            throw new InvalidMarketDataException("빈 캔들 묶음에는 첫 캔들도 마지막 캔들도 없다");
        }
        return candles;
    }

    private static void assertAscendingAndDistinct(List<Candle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            Instant previous = candles.get(i - 1).openTime();
            Instant current = candles.get(i).openTime();
            if (previous.equals(current)) {
                throw new DuplicateCandleException(current);
            }
            if (current.isBefore(previous)) {
                throw new InvalidMarketDataException(
                        "캔들은 시간 오름차순이어야 한다: %s 뒤에 %s".formatted(previous, current));
            }
        }
    }
}
