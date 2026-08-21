package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 조회 조건을 객체 하나로 묶는 이유는 인자 순서 사고를 막기 위해서다.
 * {@code (Symbol, CandleInterval, Instant, Instant)} 면 {@code from} 과 {@code to} 가 뒤바뀐
 * 호출도 컴파일을 통과한다.
 */
class CandleQueryTest {

    private static final TimeRange RANGE = new TimeRange(
            Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"));

    @Test
    void 종목과_주기와_구간을_함께_들고_다닌다() {
        CandleQuery query = new CandleQuery(Symbol.BTC_USDT, CandleInterval.ONE_HOUR, RANGE);

        assertThat(query.symbol()).isEqualTo(Symbol.BTC_USDT);
        assertThat(query.interval()).isEqualTo(CandleInterval.ONE_HOUR);
        assertThat(query.range()).isEqualTo(RANGE);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new CandleQuery(null, CandleInterval.ONE_HOUR, RANGE))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new CandleQuery(Symbol.BTC_USDT, null, RANGE))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new CandleQuery(Symbol.BTC_USDT, CandleInterval.ONE_HOUR, null))
                .isInstanceOf(InvalidValueException.class);
    }
}
