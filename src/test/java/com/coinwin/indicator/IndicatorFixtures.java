package com.coinwin.indicator;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import java.util.stream.IntStream;

/**
 * 지표 테스트용 캔들. 일목과 볼린저가 같은 것을 쓴다.
 *
 * <p>{@link #rising} 이 <b>단조 증가</b> 시리즈인 것이 핵심이다. 단조 증가에서는 어느 구간이든
 * 최고가가 마지막 캔들, 최저가가 첫 캔들에 있으므로 이동 최대·최소가 손으로 풀린다. 지표 값을
 * 기댓값으로 박아 두려면 그 값이 구현과 무관하게 유도돼야 한다.
 *
 * <p>단조 입력만으로는 "언제나 마지막 고가를 쓴다" 같은 버그가 통과하므로, 극값이 구간 중간에
 * 오는 비단조 캔들은 {@link #candle} 로 직접 조립해서 따로 검사한다.
 */
public final class IndicatorFixtures {

    public static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    public static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;

    private static final int BASE = 60000;

    private IndicatorFixtures() {
    }

    public static Instant hour(int index) {
        return T0.plus(INTERVAL.length().multipliedBy(index));
    }

    /** 시가와 종가가 같은 캔들. 일목은 고가·저가만, 볼린저는 종가만 쓰므로 시가는 자유롭다. */
    public static Candle candle(int index, String high, String low, String close) {
        return new Candle(hour(index), Price.of(close), Price.of(high), Price.of(low),
                Price.of(close), Quantity.of("1"));
    }

    /**
     * {@code close = 60000 + step × index}, 고가·저가는 종가를 {@code half} 만큼 감싼 시리즈.
     *
     * <p>{@code step = 0} 이면 종가가 일정한 시리즈가 된다 — 볼린저 표준편차 0 검사에 쓴다.
     */
    public static CandleSeries rising(int count, int step, int half) {
        return new CandleSeries(IntStream.range(0, count)
                .mapToObj(i -> candle(i,
                        String.valueOf(BASE + step * i + half),
                        String.valueOf(BASE + step * i - half),
                        String.valueOf(BASE + step * i)))
                .toList());
    }
}
