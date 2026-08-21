package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@code "1h"} 같은 코드는 바이낸스 표기이자 저장 키다. 도메인이 이 문자열을 들고 있는 것은
 * HTTP 를 아는 것과 다르다 — 어댑터 세 곳이 같은 표기를 쓰지 않으면 같은 캔들이 두 줄이 된다.
 */
class CandleIntervalTest {

    @Test
    void 코드로_주기를_찾는다() {
        assertThat(CandleInterval.ofCode("1h")).isEqualTo(CandleInterval.ONE_HOUR);
        assertThat(CandleInterval.ofCode("4h")).isEqualTo(CandleInterval.FOUR_HOURS);
    }

    @Test
    void 모르는_코드는_거부된다() {
        assertThatThrownBy(() -> CandleInterval.ofCode("7m"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("7m");
    }

    @Test
    void null_코드는_거부된다() {
        assertThatThrownBy(() -> CandleInterval.ofCode(null))
                .isInstanceOf(InvalidValueException.class);
    }

    /** 페이지를 이어 받을 때 다음 시작 시각이 {@code 마지막 openTime + length} 다. */
    @Test
    void 주기는_자기_길이를_안다() {
        assertThat(CandleInterval.ONE_MINUTE.length()).isEqualTo(Duration.ofMinutes(1));
        assertThat(CandleInterval.FOUR_HOURS.length()).isEqualTo(Duration.ofHours(4));
        assertThat(CandleInterval.ONE_DAY.length()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void 모든_주기의_코드가_서로_다르다() {
        assertThat(java.util.Arrays.stream(CandleInterval.values()).map(CandleInterval::code))
                .doesNotHaveDuplicates()
                .hasSize(CandleInterval.values().length);
    }
}
