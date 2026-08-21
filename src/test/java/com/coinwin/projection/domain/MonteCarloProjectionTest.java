package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MonteCarloProjectionTest {

    private static final ProjectionSpec SPEC = new ProjectionSpec(
            Money.of("1000"),
            new TradingEdge(Percentage.of("50"), new BigDecimal("2"), Percentage.of("2")),
            new TradeFrequency(2, 50));

    @Test
    void 시행_횟수만큼의_결과가_담긴다() {
        assertThat(new MonteCarloProjection(SPEC, 200, 20260821L).run().runs()).isEqualTo(200);
    }

    /**
     * 결정론. 같은 파라미터로 다시 돌린 결과가 다르면 이 도구로 무엇을 비교하든 의미가 없다.
     * testing.md: "동일 파라미터 재실행 시 결과가 완전히 동일해야 한다."
     */
    @Test
    void 같은_시드로_다시_돌리면_결과가_완전히_같다() {
        ProjectionDistribution 첫번째 = new MonteCarloProjection(SPEC, 200, 20260821L).run();
        ProjectionDistribution 두번째 = new MonteCarloProjection(SPEC, 200, 20260821L).run();

        assertThat(첫번째.outcomes()).isEqualTo(두번째.outcomes());
    }

    @Test
    void 시드가_다르면_결과가_달라진다() {
        ProjectionDistribution 첫번째 = new MonteCarloProjection(SPEC, 200, 20260821L).run();
        ProjectionDistribution 다른_시드 = new MonteCarloProjection(SPEC, 200, 20260822L).run();

        assertThat(첫번째.outcomes()).isNotEqualTo(다른_시드.outcomes());
    }

    /** 시행마다 난수원을 새로 만들면 200 번이 전부 같은 경로가 된다. 난수열은 이어져야 한다. */
    @Test
    void 시행마다_다른_경로를_지나간다() {
        ProjectionDistribution 분포 = new MonteCarloProjection(SPEC, 200, 20260821L).run();

        assertThat(new HashSet<>(분포.outcomes())).hasSizeGreaterThan(1);
    }

    @Test
    void 시행_횟수는_1_이상이고_상한이_있다() {
        assertThatThrownBy(() -> new MonteCarloProjection(SPEC, 0, 1L))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("시행 횟수");
        assertThatThrownBy(() -> new MonteCarloProjection(SPEC, 10_001, 1L))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("10000");
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new MonteCarloProjection(null, 10, 1L))
                .isInstanceOf(InvalidValueException.class);
    }
}
