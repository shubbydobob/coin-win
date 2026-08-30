package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지표가 어느 무리에 속하는가.
 *
 * <p><b>이 구분이 왜 타입인가.</b> 추세 무리와 되돌림 무리는 <b>같은 사실에 반대 뜻을
 * 붙인다</b> — 종가가 볼린저 상단 위인 것은 되돌림에게 과열이고 추세에게 돌파다. 그런데
 * 둘 다 「위」 딱지를 받으므로, 다섯을 한 번에 세면 같은 사실이 두 번 세어지거나 반대
 * 사실이 상쇄된다. 세는 자리가 화면이든 서버든 그것은 산술적으로 성립하지 않는다.
 *
 * <p>근거는 {@code docs/spec/indicator-usage.md} § 2 다.
 */
class IndicatorFamilyTest {

    @Test
    @DisplayName("추세 무리는 일목·MACD·이동평균이다")
    void 추세_무리() {
        assertThat(무리(IndicatorFamily.TREND))
                .containsExactlyInAnyOrder(
                        IndicatorKind.ICHIMOKU, IndicatorKind.MACD, IndicatorKind.MOVING_AVERAGE);
    }

    @Test
    @DisplayName("되돌림 무리는 RSI·볼린저다")
    void 되돌림_무리() {
        assertThat(무리(IndicatorFamily.REVERSION))
                .containsExactlyInAnyOrder(IndicatorKind.RSI, IndicatorKind.BOLLINGER);
    }

    /**
     * <b>무리 없는 지표가 생기면 세는 쪽이 조용히 그것을 뺀다.</b> 화면은 두 무리를 그리므로
     * 어느 무리에도 안 들어간 지표는 아무 데도 나타나지 않고, 그것은 없는 것과 같다.
     */
    @Test
    @DisplayName("모든 지표는 어느 한 무리에 속한다")
    void 무리_없는_지표는_없다() {
        assertThat(Arrays.stream(IndicatorKind.values()).map(IndicatorKind::family))
                .doesNotContainNull()
                .hasSize(IndicatorKind.values().length);
    }

    /** 화면에 적히는 이름은 지표가 갖는다. 세는 쪽이 문자열을 다시 쓰면 둘이 갈라진다. */
    @Test
    @DisplayName("지표 이름은 서로 다르다")
    void 이름은_겹치지_않는다() {
        assertThat(Arrays.stream(IndicatorKind.values()).map(IndicatorKind::label))
                .doesNotHaveDuplicates()
                .hasSize(IndicatorKind.values().length);
    }

    private static java.util.List<IndicatorKind> 무리(IndicatorFamily family) {
        return Arrays.stream(IndicatorKind.values())
                .filter(kind -> kind.family() == family)
                .toList();
    }
}
