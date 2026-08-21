package com.coinwin.projection.domain;

import static com.coinwin.projection.domain.TradeOutcome.LOSS;
import static com.coinwin.projection.domain.TradeOutcome.WIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectionSpecTest {

    /** 거래당 10% 리스크, 손익비 2 → 승리 배수 1.2, 패배 배수 0.9. 순서 효과가 눈에 보이는 크기다. */
    private static final TradingEdge WIDE_SWING_EDGE =
            new TradingEdge(Percentage.of("50"), new BigDecimal("2"), Percentage.of("10"));

    private static ProjectionSpec 조건(TradingEdge edge) {
        return new ProjectionSpec(Money.of("1000"), edge, new TradeFrequency(2, 50));
    }

    /**
     * 고정 비율 복리는 곱셈이고 곱셈은 교환법칙을 따른다. 승패 구성이 같으면 순서가 어떻든
     * 최종 자산은 같은 값이다. 이것이 성립하지 않는다면 반올림이 새고 있다는 뜻이다.
     */
    @Test
    void 승패_순서가_달라도_구성이_같으면_최종_자산은_같다() {
        EquityCurve 먼저_이긴_경우 = 조건(WIDE_SWING_EDGE).project(List.of(WIN, LOSS, LOSS, WIN));
        EquityCurve 먼저_진_경우 = 조건(WIDE_SWING_EDGE).project(List.of(LOSS, WIN, WIN, LOSS));

        assertThat(먼저_이긴_경우.finalEquity()).isEqualTo(Money.of("1166.40"));
        assertThat(먼저_진_경우.finalEquity()).isEqualTo(먼저_이긴_경우.finalEquity());
    }

    /** 순서가 남기는 자국은 최종 자산이 아니라 낙폭이다. 같은 결과에 두 배 가까운 낙폭 차이가 난다. */
    @Test
    void 같은_구성이어도_순서가_바뀌면_최대낙폭은_달라진다() {
        EquityCurve 먼저_이긴_경우 = 조건(WIDE_SWING_EDGE).project(List.of(WIN, LOSS, LOSS, WIN));
        EquityCurve 먼저_진_경우 = 조건(WIDE_SWING_EDGE).project(List.of(LOSS, WIN, WIN, LOSS));

        assertThat(먼저_이긴_경우.maxDrawdown()).isEqualTo(Percentage.of("19"));
        assertThat(먼저_진_경우.maxDrawdown()).isEqualTo(Percentage.of("10"));
    }

    /**
     * 각 점은 직전 점이 아니라 초기 자본에 누적 배수를 곱해 얻는다.
     *
     * <p>점마다 센트로 반올림한 값을 다시 곱하면 5거래 만에 1센트가 어긋난다
     * (1159.28 vs 1159.27). 거래 수가 늘수록 벌어지고, 그러면 순서만 다른 두 경로의
     * 최종 자산이 갈라져 위 교환법칙 테스트가 무너진다.
     */
    @Test
    void 각_점은_직전_점이_아니라_초기_자본에서_계산되므로_반올림이_누적되지_않는다() {
        TradingEdge threePercentEdge =
                new TradingEdge(Percentage.of("50"), BigDecimal.ONE, Percentage.of("3"));

        EquityCurve curve = 조건(threePercentEdge).project(List.of(WIN, WIN, WIN, WIN, WIN));

        // 1000 × 1.03^5 = 1159.2740...
        assertThat(curve.finalEquity()).isEqualTo(Money.of("1159.27"));
    }

    @Test
    void 곡선의_첫_점은_거래_이전의_초기_자본이다() {
        EquityCurve curve = 조건(WIDE_SWING_EDGE).project(List.of(WIN, LOSS));

        assertThat(curve.points()).containsExactly(
                Money.of("1000"), Money.of("1200"), Money.of("1080"));
    }

    @Test
    void 시드로_만든_경로는_거래_빈도가_정한_횟수만큼_거래한다() {
        // 주 2회 × 50주 = 100 거래
        assertThat(조건(WIDE_SWING_EDGE).simulate(11).trades()).isEqualTo(100);
    }

    @Test
    void 같은_시드는_같은_경로를_만든다() {
        assertThat(조건(WIDE_SWING_EDGE).simulate(11).points())
                .isEqualTo(조건(WIDE_SWING_EDGE).simulate(11).points());
    }

    @Test
    void 시드가_다르면_경로가_달라진다() {
        assertThat(조건(WIDE_SWING_EDGE).simulate(11).points())
                .isNotEqualTo(조건(WIDE_SWING_EDGE).simulate(12).points());
    }

    @Test
    void 초기_자본은_0보다_커야_한다() {
        assertThatThrownBy(() -> new ProjectionSpec(
                Money.of("0"), WIDE_SWING_EDGE, new TradeFrequency(2, 50)))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("초기 자본");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new ProjectionSpec(null, WIDE_SWING_EDGE, new TradeFrequency(2, 50)))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ProjectionSpec(
                Money.of("1000"), null, new TradeFrequency(2, 50)))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ProjectionSpec(Money.of("1000"), WIDE_SWING_EDGE, null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> 조건(WIDE_SWING_EDGE).project(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
