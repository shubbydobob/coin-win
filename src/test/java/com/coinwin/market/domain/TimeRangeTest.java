package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 조회 구간은 <b>반열림</b> {@code [from, to)} 이다.
 *
 * <p>양끝을 다 포함하면 연속한 두 구간을 이어 받을 때 경계의 캔들이 두 번 온다. 그것이
 * 그대로 "증분 저장 중복" 이 된다. 구간 정의에서 막는 편이 어댑터마다 막는 것보다 낫다.
 */
class TimeRangeTest {

    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void 시작은_포함하고_끝은_포함하지_않는다() {
        TimeRange range = new TimeRange(FROM, TO);

        assertThat(range.contains(FROM)).isTrue();
        assertThat(range.contains(TO)).isFalse();
    }

    @Test
    void 구간_안의_시각은_포함한다() {
        assertThat(new TimeRange(FROM, TO).contains(Instant.parse("2026-08-01T12:00:00Z")))
                .isTrue();
    }

    @Test
    void 구간_앞의_시각은_포함하지_않는다() {
        assertThat(new TimeRange(FROM, TO).contains(Instant.parse("2026-07-31T23:59:59Z")))
                .isFalse();
    }

    /** 연속한 두 구간이 경계를 공유해도 같은 시각이 양쪽에 들지 않는다. */
    @Test
    void 경계를_공유한_두_구간은_같은_시각을_함께_포함하지_않는다() {
        TimeRange 앞 = new TimeRange(FROM, TO);
        TimeRange 뒤 = new TimeRange(TO, Instant.parse("2026-08-03T00:00:00Z"));

        assertThat(앞.contains(TO)).isFalse();
        assertThat(뒤.contains(TO)).isTrue();
    }

    @Test
    void 끝이_시작보다_앞이면_거부된다() {
        assertThatThrownBy(() -> new TimeRange(TO, FROM))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    /** 시작과 끝이 같으면 어떤 시각도 담지 못한다. 조회로서 성립하지 않는다. */
    @Test
    void 시작과_끝이_같으면_거부된다() {
        assertThatThrownBy(() -> new TimeRange(FROM, FROM))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void null_은_거부된다() {
        assertThatThrownBy(() -> new TimeRange(null, TO)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TimeRange(FROM, null)).isInstanceOf(InvalidValueException.class);
    }
}
