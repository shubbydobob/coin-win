package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 표본 안에서 현재값이 어디쯤인가.
 *
 * <p><b>배수가 아니라 위치로 말하는 이유</b>는 펀딩비 때문이다. 부호가 바뀌는 값에서
 * "평소 대비 몇 배" 는 0 근처에서 무한대로 튀고 부호가 반대면 뜻 자체가 사라진다.
 * 위치는 부호에 무너지지 않는다.
 */
class PercentileTest {

    @Test
    void 표본의_절반보다_크면_위쪽이다() {
        // [1, 2, 3, 4] 에서 2.5 보다 작은 것이 둘, 같은 것은 없다 → 2 / 4
        assertThat(위치("2.5", "1", "2", "3", "4").value()).isEqualByComparingTo("0.5000");
    }

    @Test
    void 가장_작으면_0_이고_가장_크면_1_에_가깝다() {
        assertThat(위치("0", "1", "2", "3", "4").value()).isEqualByComparingTo("0.0000");
        assertThat(위치("9", "1", "2", "3", "4").value()).isEqualByComparingTo("1.0000");
    }

    /**
     * <b>동점 처리가 이 클래스의 존재 이유다.</b>
     *
     * <p>실제 펀딩비는 연속으로 정확히 같은 값이 나온다({@code 0.00010000} 이 이어서 두 번).
     * 작은 것만 세면 하위 0%, 작거나 같은 것을 세면 상위 0% 가 되어 <b>같은 입력에서 정반대
     * 답</b>이 나온다. 동점의 절반만 아래로 세면 그 둘의 가운데인 0.5 가 된다.
     */
    @Test
    void 표본이_전부_같은_값이면_한가운데다() {
        assertThat(위치("5", "5", "5", "5", "5").value()).isEqualByComparingTo("0.5000");
    }

    @Test
    void 동점이_섞이면_그_절반만_아래로_센다() {
        // [1, 2, 2, 2] 에서 2 는 작은 것 하나 + 동점 셋의 절반 1.5 → 2.5 / 4
        assertThat(위치("2", "1", "2", "2", "2").value()).isEqualByComparingTo("0.6250");
    }

    @Test
    void 상위_비율은_위치의_반대편이다() {
        assertThat(위치("2.5", "1", "2", "3", "4").topPercent().value()).isEqualByComparingTo("50.0000");
        // 표본에 있는 값은 동점이 걸린다 — [1,2,3,4] 의 3 은 (2 + 0.5) / 4 = 0.625 → 상위 37.5%
        assertThat(위치("3", "1", "2", "3", "4").topPercent().value()).isEqualByComparingTo("37.5000");
        assertThat(위치("9", "1", "2", "3", "4").topPercent().value()).isEqualByComparingTo("0.0000");
    }

    @Test
    void 양_끝_5퍼센트만_이상치다() {
        assertThat(new Percentile(new BigDecimal("0.9600")).isOutlier()).isTrue();
        assertThat(new Percentile(new BigDecimal("0.9500")).isOutlier()).isFalse();
        assertThat(new Percentile(new BigDecimal("0.0400")).isOutlier()).isTrue();
        assertThat(new Percentile(new BigDecimal("0.0500")).isOutlier()).isFalse();
        assertThat(new Percentile(new BigDecimal("0.5000")).isOutlier()).isFalse();
    }

    @Test
    void 위치는_0_과_1_사이여야_한다() {
        assertThatThrownBy(() -> new Percentile(new BigDecimal("1.0001")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Percentile(new BigDecimal("-0.0001")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 표본이_비면_위치를_낼_수_없다() {
        assertThatThrownBy(() -> Percentile.of(BigDecimal.ONE, java.util.List.of()))
                .isInstanceOf(InvalidValueException.class);
    }

    private static Percentile 위치(String current, String... samples) {
        return Percentile.of(
                new BigDecimal(current),
                java.util.Arrays.stream(samples).map(BigDecimal::new).toList());
    }
}
