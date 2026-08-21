package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.Test;

class TradingEdgeTest {

    private static final BigDecimal REWARD_2R = new BigDecimal("2");

    private static TradingEdge 우위(String 승률, String 손익비, String 리스크) {
        return new TradingEdge(
                Percentage.of(승률), new BigDecimal(손익비), Percentage.of(리스크));
    }

    private static RandomGenerator 난수원(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void 승리하면_리스크_비율에_손익비를_곱한_만큼_자산이_늘어난다() {
        // 거래당 2% 를 걸고 손익비 2 → 이기면 4% 증가
        assertThat(우위("50", "2", "2").factorFor(TradeOutcome.WIN))
                .isEqualByComparingTo("1.04");
    }

    @Test
    void 패배하면_리스크_비율만큼_줄어든다() {
        assertThat(우위("50", "2", "2").factorFor(TradeOutcome.LOSS))
                .isEqualByComparingTo("0.98");
    }

    @Test
    void 거래당_기댓값은_R_배수로_표현된다() {
        // 승률 50% × 손익비 2 - 패률 50% = 0.5R
        assertThat(우위("50", "2", "2").expectancyPerTrade()).isEqualByComparingTo("0.5");
    }

    /** Phase 2 의 출발점. 이 둘은 기댓값이 같지만 자산 곡선의 생김새는 전혀 다르다. */
    @Test
    void 승률_50_손익비_2_와_승률_25_손익비_5_는_기댓값이_같다() {
        assertThat(우위("25", "5", "2").expectancyPerTrade())
                .isEqualByComparingTo(우위("50", "2", "2").expectancyPerTrade());
    }

    @Test
    void 승률만큼의_비율로_승리를_뽑는다() {
        TradingEdge edge = 우위("45", "2", "2");
        RandomGenerator random = 난수원(42);

        long 승리 = 0;
        for (int i = 0; i < 10_000; i++) {
            if (edge.drawOutcome(random) == TradeOutcome.WIN) {
                승리++;
            }
        }

        assertThat(승리).isBetween(4300L, 4700L);
    }

    @Test
    void 승률_100이면_항상_승리하고_0이면_항상_패배한다() {
        assertThat(우위("100", "2", "2").drawOutcome(난수원(1))).isEqualTo(TradeOutcome.WIN);
        assertThat(우위("0", "2", "2").drawOutcome(난수원(1))).isEqualTo(TradeOutcome.LOSS);
    }

    @Test
    void 같은_시드는_같은_승패_순서를_낸다() {
        TradingEdge edge = 우위("45", "2", "2");
        RandomGenerator 첫번째 = 난수원(7);
        RandomGenerator 두번째 = 난수원(7);

        for (int i = 0; i < 50; i++) {
            assertThat(edge.drawOutcome(첫번째)).isEqualTo(edge.drawOutcome(두번째));
        }
    }

    @Test
    void 승률은_100을_넘을_수_없다() {
        assertThatThrownBy(() -> 우위("100.0001", "2", "2"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("승률");
    }

    @Test
    void 손익비는_0보다_커야_한다() {
        assertThatThrownBy(() -> 우위("50", "0", "2"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("손익비");
    }

    @Test
    void 거래당_리스크_비율은_0보다_크고_100_이하여야_한다() {
        assertThatThrownBy(() -> 우위("50", "2", "0"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("리스크");
        assertThatThrownBy(() -> 우위("50", "2", "100.0001"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("리스크");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new TradingEdge(null, REWARD_2R, Percentage.of("2")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradingEdge(Percentage.of("50"), null, Percentage.of("2")))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new TradingEdge(Percentage.of("50"), REWARD_2R, null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> 우위("50", "2", "2").factorFor(null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> 우위("50", "2", "2").drawOutcome(null))
                .isInstanceOf(InvalidValueException.class);
    }
}
