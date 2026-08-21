package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectionDistributionTest {

    private static ProjectionDistribution 분포(String... 최종자산) {
        return new ProjectionDistribution(
                Arrays.stream(최종자산)
                        .map(자산 -> new ProjectionOutcome(Money.of(자산), Percentage.of("0")))
                        .toList(),
                Money.of("1000"));
    }

    @Test
    void 시행_횟수는_담긴_결과의_수다() {
        assertThat(분포("800", "900", "1000").runs()).isEqualTo(3);
    }

    @Test
    void 백분위_0은_최악이고_100은_최선이며_50은_중앙값이다() {
        // 입력 순서와 무관하게 정렬해서 고른다
        ProjectionDistribution 분포 = 분포("1200", "800", "1100", "1000", "900");

        assertThat(분포.equityPercentile(0)).isEqualTo(Money.of("800"));
        assertThat(분포.equityPercentile(50)).isEqualTo(Money.of("1000"));
        assertThat(분포.equityPercentile(100)).isEqualTo(Money.of("1200"));
    }

    /**
     * 보간하지 않는다. 실제로 일어난 시행 중 하나를 가리켜야 "이런 결과가 나왔다" 고
     * 말할 수 있기 때문이다. 짝수 시행에서는 아래쪽 값이 중앙값이 된다.
     */
    @Test
    void 짝수_시행의_중앙값은_보간하지_않고_아래쪽을_고른다() {
        assertThat(분포("800", "900", "1100", "1200").equityPercentile(50))
                .isEqualTo(Money.of("900"));
    }

    @Test
    void 백분위는_0과_100_사이여야_한다() {
        assertThatThrownBy(() -> 분포("800").equityPercentile(101))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("백분위");
        assertThatThrownBy(() -> 분포("800").drawdownPercentile(-1))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("백분위");
    }

    @Test
    void 낙폭_백분위는_낙폭_기준으로_따로_정렬한다() {
        ProjectionDistribution 분포 = new ProjectionDistribution(List.of(
                new ProjectionOutcome(Money.of("1200"), Percentage.of("30")),
                new ProjectionOutcome(Money.of("800"), Percentage.of("10")),
                new ProjectionOutcome(Money.of("1000"), Percentage.of("20"))),
                Money.of("1000"));

        assertThat(분포.drawdownPercentile(0)).isEqualTo(Percentage.of("10"));
        assertThat(분포.drawdownPercentile(50)).isEqualTo(Percentage.of("20"));
        assertThat(분포.drawdownPercentile(100)).isEqualTo(Percentage.of("30"));
    }

    @Test
    void 손실_확률은_초기_자본에_못_미친_시행의_비율이다() {
        // 800, 900 두 건이 1000 미만 → 5 회 중 2 회
        assertThat(분포("800", "900", "1000", "1100", "1200").lossProbability())
                .isEqualTo(Percentage.of("40"));
    }

    @Test
    void 본전으로_끝난_시행은_손실이_아니다() {
        assertThat(분포("1000", "1000").lossProbability()).isEqualTo(Percentage.of("0"));
    }

    @Test
    void 시행이_한_번도_없는_분포는_거부된다() {
        assertThatThrownBy(() -> new ProjectionDistribution(List.of(), Money.of("1000")))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("시행");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new ProjectionDistribution(null, Money.of("1000")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ProjectionDistribution(
                List.of(new ProjectionOutcome(Money.of("1000"), Percentage.of("0"))), null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> ProjectionOutcome.of(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
