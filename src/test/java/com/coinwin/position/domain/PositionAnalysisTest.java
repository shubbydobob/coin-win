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

    /**
     * 표시용 손익비를 반올림한 뒤 1.5 와 비교하면 이 구간이 통째로 빠져나간다.
     * 1.495 는 HALF_UP 으로 1.50 이 되어 경고가 꺼진다.
     */
    @Test
    void 손익비_1_495는_반올림하면_1_50이지만_여전히_경고_대상이다() {
        PositionAnalysis 분석 = 손익비_계획("74950").analyze(BUDGET, MMR04);

        assertThat(분석.weakRiskReward()).isTrue();
        assertThat(분석.riskRewardRatio()).isEqualByComparingTo("1.49");
    }

    @Test
    void 손익비가_정확히_1_5면_경고하지_않는다() {
        PositionAnalysis 분석 = 손익비_계획("75000").analyze(BUDGET, MMR04);

        assertThat(분석.weakRiskReward()).isFalse();
        assertThat(분석.riskRewardRatio()).isEqualByComparingTo("1.50");
    }

    @Test
    void 표시용_손익비는_기준을_넘은_것처럼_보이지_않게_버림한다() {
        // 1.4999 를 1.50 으로 표시하면 경고와 화면이 서로 모순된다
        assertThat(손익비_계획("74999").riskRewardRatio()).isEqualByComparingTo("1.49");
        assertThat(손익비_계획("74999").weakRiskReward()).isTrue();
    }

    /** 손절 거리를 10,000 으로 고정해 익절가만 바꾸면 손익비가 그대로 나온다. */
    private static PositionPlan 손익비_계획(String takeProfit) {
        return new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "100")),
                Price.of("50000"),
                Price.of(takeProfit),
                3);
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
                List.of(), Money.of("31.47"), Money.of("800"), new BigDecimal("2.33"), false))
                .isInstanceOf(InvalidPositionPlanException.class);
    }

    @Test
    void null_체결_상태는_거부된다() {
        assertThatThrownBy(() -> new PositionAnalysis(
                null, Money.of("31.47"), Money.of("800"), new BigDecimal("2.33"), false))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 체결_상태가_하나뿐이면_1차와_전량이_같은_상태를_가리킨다() {
        FillState 단일 = new FillState(1, Price.of("60000"), Quantity.of("0.01"),
                Price.of("54240"), Money.of("16"));

        PositionAnalysis 분석 = new PositionAnalysis(
                List.of(단일), Money.of("60"), Money.of("800"), new BigDecimal("2.00"), false);

        assertThat(분석.afterFirstEntry()).isEqualTo(분석.whenFullyFilled());
    }
}
