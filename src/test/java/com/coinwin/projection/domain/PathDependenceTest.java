package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Phase 2 완료 조건 — <b>"같은 기댓값에서도 경로에 따라 결과가 갈리는 것이 수치로 나온다."</b>
 *
 * <p>기댓값은 평균이고, 평균은 한 번의 매매 인생에서 실제로 일어나는 일이 아니다.
 * 이 테스트가 증명하려는 것은 두 가지다.
 *
 * <ol>
 *   <li>같은 조건을 반복해도 결과는 넓게 흩어진다 — 최선과 최악이 몇 배로 갈린다
 *   <li>기댓값이 같아도 승률이 낮은 쪽이 훨씬 깊은 낙폭을 지나간다 — 같은 목적지, 다른 길
 * </ol>
 */
class PathDependenceTest {

    private static final int RUNS = 300;
    private static final long SEED = 20260821L;
    private static final BigDecimal TWO = new BigDecimal("2");

    /** 승률 50% / 손익비 2 / 거래당 2%. 기댓값 0.5R. */
    private static final TradingEdge HIGH_WIN_RATE =
            new TradingEdge(Percentage.of("50"), new BigDecimal("2"), Percentage.of("2"));

    /** 승률 25% / 손익비 5 / 거래당 2%. 기댓값은 위와 <b>같은</b> 0.5R. */
    private static final TradingEdge LOW_WIN_RATE =
            new TradingEdge(Percentage.of("25"), new BigDecimal("5"), Percentage.of("2"));

    private static ProjectionDistribution 시뮬레이션(TradingEdge edge) {
        return new MonteCarloProjection(
                new ProjectionSpec(Money.of("1000"), edge, new TradeFrequency(2, 50)),
                RUNS, SEED).run();
    }

    @Test
    void 두_조합의_거래당_기댓값은_같다() {
        assertThat(LOW_WIN_RATE.expectancyPerTrade())
                .isEqualByComparingTo(HIGH_WIN_RATE.expectancyPerTrade());
    }

    @Test
    void 같은_조건을_300회_반복해도_결과는_넓게_흩어진다() {
        ProjectionDistribution 분포 = 시뮬레이션(HIGH_WIN_RATE);

        Money 하위5퍼센트 = 분포.equityPercentile(5);
        Money 상위5퍼센트 = 분포.equityPercentile(95);

        assertThat(상위5퍼센트.value()).isGreaterThan(하위5퍼센트.value().multiply(TWO));
    }

    @Test
    void 기댓값이_같아도_승률이_낮은_쪽이_더_깊은_낙폭을_지나간다() {
        Percentage 높은_승률의_낙폭 = 시뮬레이션(HIGH_WIN_RATE).drawdownPercentile(50);
        Percentage 낮은_승률의_낙폭 = 시뮬레이션(LOW_WIN_RATE).drawdownPercentile(50);

        assertThat(낮은_승률의_낙폭.value()).isGreaterThan(높은_승률의_낙폭.value());
    }

    @Test
    void 기댓값이_같아도_승률이_낮은_쪽이_손실로_끝날_확률이_높다() {
        Percentage 높은_승률의_손실_확률 = 시뮬레이션(HIGH_WIN_RATE).lossProbability();
        Percentage 낮은_승률의_손실_확률 = 시뮬레이션(LOW_WIN_RATE).lossProbability();

        assertThat(낮은_승률의_손실_확률.value())
                .isGreaterThan(높은_승률의_손실_확률.value());
    }
}
