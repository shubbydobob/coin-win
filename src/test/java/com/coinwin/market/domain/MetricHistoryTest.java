package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 지표 하나의 시계열 표본.
 *
 * <p>이 타입이 하는 일은 둘뿐이다 — 표본을 들고 있는 것과, <b>표본이 모자라면 위치를 내지
 * 않는 것</b>. 뒤쪽이 요점이다.
 */
class MetricHistoryTest {

    @Test
    void 표본이_충분하면_위치를_낸다() {
        MetricHistory history = 표본(1, 100);

        assertThat(history.positionOf(new BigDecimal("50"), 20))
                .hasValueSatisfying(위치 -> assertThat(위치.value()).isEqualByComparingTo("0.4950"));
    }

    /**
     * <b>말할 수 없는 것을 수치로 적지 않는다.</b> "표본 3개 중 상위 33%" 는 수치의 모양만 갖춘
     * 거짓말이다. 손익비와 청산가에서 이미 두 번 세운 규칙과 같다.
     */
    @Test
    void 표본이_최소치에_못_미치면_위치를_내지_않는다() {
        assertThat(표본(1, 19).positionOf(new BigDecimal("10"), 20)).isEmpty();
        assertThat(표본(1, 20).positionOf(new BigDecimal("10"), 20)).isPresent();
    }

    @Test
    void 표본이_비어_있어도_예외가_아니라_빈_결과다() {
        assertThat(new MetricHistory(List.of()).positionOf(BigDecimal.ONE, 1)).isEmpty();
    }

    @Test
    void 표본_개수를_말할_수_있다() {
        assertThat(표본(1, 30).sampleCount()).isEqualTo(30);
    }

    @Test
    void 표본은_null_일_수_없다() {
        assertThatThrownBy(() -> new MetricHistory(null)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 최소치는_1_보다_작을_수_없다() {
        assertThatThrownBy(() -> 표본(1, 5).positionOf(BigDecimal.ONE, 0))
                .isInstanceOf(InvalidValueException.class);
    }

    /**
     * 표본을 그대로 들고 있지 않는다. 밖에서 목록을 고치면 이미 낸 위치와 다음에 낼 위치가
     * 달라진다.
     */
    @Test
    void 넘어온_목록이_바뀌어도_표본은_그대로다() {
        List<BigDecimal> 원본 = new java.util.ArrayList<>(List.of(BigDecimal.ONE, BigDecimal.TEN));
        MetricHistory history = new MetricHistory(원본);

        원본.clear();

        assertThat(history.sampleCount()).isEqualTo(2);
    }

    private static MetricHistory 표본(int from, int to) {
        return new MetricHistory(
                IntStream.rangeClosed(from, to).mapToObj(BigDecimal::valueOf).toList());
    }
}
