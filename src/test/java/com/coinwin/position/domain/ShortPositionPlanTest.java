package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import org.junit.jupiter.api.Test;

/**
 * 숏 방향은 손절가·익절가가 놓이는 쪽과 청산가 계수의 부호가 롱과 반대다.
 *
 * <p>기준 계획: 숏 50% 분할, 60,000 / 62,000 진입, 손절 64,000, 익절 55,000, 10배,
 * 잔고 800 USDT 의 2%. 전량 평단 61,000, 청산가 계수 {@code 1 + 1/10 - 0.4% = 1.096}.
 */
class ShortPositionPlanTest {

    private static final MaintenanceMarginPolicy MMR04 =
            FixedMaintenanceMarginPolicy.btcUsdtApproximation();
    private static final RiskBudget BUDGET = RiskBudget.of("800", "2");

    private static PositionPlan 숏_50퍼센트_분할() {
        return new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("62000", "50")),
                Price.of("64000"),
                Price.of("55000"),
                10);
    }

    @Test
    void 숏_포지션의_청산가는_평단보다_위에_있다() {
        PositionAnalysis 분석 = 숏_50퍼센트_분할().analyze(BUDGET, MMR04);

        assertThat(분석.afterFirstEntry().liquidationPrice()).isEqualTo(Price.of("65760"));
        assertThat(분석.whenFullyFilled().liquidationPrice()).isEqualTo(Price.of("66856"));
    }

    @Test
    void 숏도_전량_체결_손실이_리스크_금액과_일치한다() {
        PositionAnalysis 분석 = 숏_50퍼센트_분할().analyze(BUDGET, MMR04);

        assertThat(분석.afterFirstEntry().maxLoss()).isEqualTo(Money.of("10.67"));
        assertThat(분석.whenFullyFilled().maxLoss()).isEqualTo(Money.of("16.00"));
    }

    @Test
    void 숏의_총수량도_손절_거리가_결정한다() {
        // 16 / |61000-64000| = 16/3000
        assertThat(숏_50퍼센트_분할().totalQuantity(BUDGET)).isEqualTo(Quantity.of("0.00533333"));
    }

    @Test
    void 숏의_손익비는_평단_아래로_내려가는_거리로_계산된다() {
        // |55000-61000| / |61000-64000| = 6000/3000
        assertThat(숏_50퍼센트_분할().riskRewardRatio()).isEqualByComparingTo("2.00");
    }

    @Test
    void 숏_포지션은_손절가가_최고_진입가보다_높아야_한다() {
        assertThatThrownBy(() -> new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("62000", "50")),
                Price.of("61500"),
                Price.of("55000"),
                10))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("최고 진입가");
    }

    @Test
    void 숏_포지션은_익절가가_최저_진입가보다_낮아야_한다() {
        assertThatThrownBy(() -> new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("62000", "50")),
                Price.of("64000"),
                Price.of("60500"),
                10))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("최저 진입가");
    }
}
