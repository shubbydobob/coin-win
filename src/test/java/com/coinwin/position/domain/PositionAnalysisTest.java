package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 경고 플래그는 거부가 아니다. 판단에 필요한 사실을 드러낼 뿐, 계획을 막지 않는다.
 * 막아야 하는 것은 계산 결과 자체가 거짓이 되는 경우뿐이다 — 손절가가 청산가 너머인 계획.
 */
class PositionAnalysisTest {

    private static final MaintenanceMarginPolicy MMR04 =
            FixedMaintenanceMarginPolicy.btcUsdtApproximation();
    private static final RiskBudget BUDGET = RiskBudget.of("800", "2");

    @Test
    void 손익비가_1_5_미만이면_경고한다() {
        // 평단 59,000 / 손절 56,000 / 익절 62,000 → 3000 대 3000 = 1.00
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("62000"),
                10);

        PositionAnalysis 분석 = plan.analyze(BUDGET, MMR04);

        assertThat(분석.riskRewardRatio()).isEqualByComparingTo("1.00");
        assertThat(분석.weakRiskReward()).isTrue();
    }

    @Test
    void 손익비가_충분하면_경고하지_않는다() {
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("66000"),
                10);

        assertThat(plan.analyze(BUDGET, MMR04).weakRiskReward()).isFalse();
    }

    /**
     * 손절 폭이 좁을수록 수량이 커지고, 수량이 커지면 증거금이 커진다. 리스크 금액은 16 USDT 로
     * 작은데 필요 증거금은 959 USDT 라서 잔고 800 으로는 애초에 열 수 없는 계획이다.
     */
    @Test
    void 필요_증거금이_잔고를_넘으면_경고한다() {
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("59900", "50")),
                Price.of("59850"),
                Price.of("60500"),
                10);

        PositionAnalysis 분석 = plan.analyze(BUDGET, MMR04);

        assertThat(분석.requiredMargin()).isEqualTo(Money.of("959.20"));
        assertThat(분석.marginExceedsBalance()).isTrue();
    }

    @Test
    void 증거금이_잔고_안쪽이면_경고하지_않는다() {
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("66000"),
                10);

        assertThat(plan.analyze(BUDGET, MMR04).marginExceedsBalance()).isFalse();
    }

    @Test
    void 체결_상태가_하나도_없는_결과는_성립하지_않는다() {
        assertThatThrownBy(() -> new PositionAnalysis(
                List.of(), Money.of("31.47"), Money.of("800"), new BigDecimal("2.33")))
                .isInstanceOf(InvalidPositionPlanException.class);
    }

    @Test
    void null_체결_상태는_거부된다() {
        assertThatThrownBy(() -> new PositionAnalysis(
                null, Money.of("31.47"), Money.of("800"), new BigDecimal("2.33")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 체결_상태가_하나뿐이면_1차와_전량이_같은_상태를_가리킨다() {
        FillState 단일 = new FillState(1, Price.of("60000"), Quantity.of("0.01"),
                Price.of("54240"), Money.of("16"));

        PositionAnalysis 분석 = new PositionAnalysis(
                List.of(단일), Money.of("60"), Money.of("800"), new BigDecimal("2.00"));

        assertThat(분석.afterFirstEntry()).isEqualTo(분석.whenFullyFilled());
    }
}
