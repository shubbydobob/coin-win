package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import org.junit.jupiter.api.Test;

/**
 * roadmap.md Phase 1 완료 조건의 시나리오를 그대로 옮긴다.
 *
 * <p>기준 계획: 롱 50% 분할, 60,000 / 58,000 진입, 손절 56,000, 익절 66,000, 10배,
 * 잔고 800 USDT 의 2% 리스크. 손으로 계산하면
 *
 * <pre>
 *   리스크 금액 = 800 × 2%          = 16.00
 *   전량 평단   = (60000+58000)/2   = 59,000.00
 *   총수량      = 16 / |59000-56000| = 0.00533333
 * </pre>
 */
class PositionPlanTest {

    private static final MaintenanceMarginPolicy MMR_04 =
            FixedMaintenanceMarginPolicy.btcUsdtApproximation();
    private static final RiskBudget BUDGET = RiskBudget.of("800", "2");

    private static PositionPlan 롱_50퍼센트_분할() {
        return new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("66000"),
                10);
    }

    @Test
    void 분할_1차만_체결된_상태의_평단은_전량_체결_평단과_다르다() {
        PositionPlan plan = 롱_50퍼센트_분할();

        assertThat(plan.averageEntryPriceIfPartial(1)).isEqualTo(Price.of("60000"));
        assertThat(plan.averageEntryPrice()).isEqualTo(Price.of("59000"));
    }

    @Test
    void 총수량은_손절_거리가_결정한다() {
        // 16 / 3000 = 0.005333... → 수량 스케일 8
        assertThat(롱_50퍼센트_분할().totalQuantity(BUDGET)).isEqualTo(Quantity.of("0.00533333"));
    }

    @Test
    void 필요_증거금은_명목가를_레버리지로_나눈_값이다() {
        // 0.00533333 × 59000 = 314.67, ÷ 10 = 31.47
        assertThat(롱_50퍼센트_분할().requiredMargin(BUDGET)).isEqualTo(Money.of("31.47"));
    }

    @Test
    void 분할_1차만_체결된_상태의_청산가는_전량_체결과_다르다() {
        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, MMR_04);

        // 계수 = 1 - 1/10 + 0.4% = 0.904
        assertThat(분석.afterFirstEntry().liquidationPrice()).isEqualTo(Price.of("54240"));
        assertThat(분석.whenFullyFilled().liquidationPrice()).isEqualTo(Price.of("53336"));
    }

    /** 이 모듈의 존재 이유. 1차 체결 시점에 보이는 손실은 최종 손실이 아니다. */
    @Test
    void 전량_체결_시_최대손실은_1차_진입_시점에_이미_확정되어_있다() {
        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, MMR_04);

        assertThat(분석.afterFirstEntry().maxLoss()).isEqualTo(Money.of("10.67"));
        assertThat(분석.whenFullyFilled().maxLoss()).isEqualTo(Money.of("16.00"));
    }

    @Test
    void 전량_체결_시_최대손실은_걸기로_한_리스크_금액과_일치한다() {
        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, MMR_04);

        assertThat(분석.whenFullyFilled().maxLoss()).isEqualTo(BUDGET.riskAmount());
    }

    @Test
    void 부분_체결_수량은_총수량에_체결된_비중을_적용한_값이다() {
        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, MMR_04);

        assertThat(분석.afterFirstEntry().quantity()).isEqualTo(Quantity.of("0.00266667"));
        assertThat(분석.whenFullyFilled().quantity()).isEqualTo(Quantity.of("0.00533333"));
    }

    @Test
    void 체결_상태는_1건부터_전량까지_순서대로_산출된다() {
        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, MMR_04);

        assertThat(분석.fillStates()).hasSize(2);
        assertThat(분석.fillStates()).extracting(FillState::filledEntries).containsExactly(1, 2);
    }

    @Test
    void 손익비는_익절_거리를_손절_거리로_나눈_값이다() {
        // |66000-59000| / |59000-56000| = 7000/3000
        assertThat(롱_50퍼센트_분할().riskRewardRatio()).isEqualByComparingTo("2.33");
    }

    /** ADR 008: 청산가 테스트는 MMR 을 주입한다. 고정 근사치에 결합하지 않는다. */
    @Test
    void 청산가는_주입된_유지증거금률에_따라_달라진다() {
        MaintenanceMarginPolicy mmr1퍼센트 = new FixedMaintenanceMarginPolicy(Percentage.of("1.0"));

        PositionAnalysis 분석 = 롱_50퍼센트_분할().analyze(BUDGET, mmr1퍼센트);

        // 계수 = 1 - 1/10 + 1% = 0.91 → 59000 × 0.91
        assertThat(분석.whenFullyFilled().liquidationPrice()).isEqualTo(Price.of("53690"));
    }

    @Test
    void 롱_포지션은_손절가가_최저_진입가보다_낮아야_한다() {
        assertThatThrownBy(() -> new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("58500"),
                Price.of("66000"),
                10))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("최저 진입가");
    }

    @Test
    void 롱_포지션은_익절가가_최고_진입가보다_높아야_한다() {
        assertThatThrownBy(() -> new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("59500"),
                10))
                .isInstanceOf(InvalidPositionPlanException.class)
                .hasMessageContaining("최고 진입가");
    }

    @Test
    void 레버리지는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "100")),
                Price.of("56000"),
                Price.of("66000"),
                0))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 방향이_없는_계획은_거부된다() {
        assertThatThrownBy(() -> new PositionPlan(
                null,
                EntryLadder.of(PlannedEntry.of("60000", "100")),
                Price.of("56000"),
                Price.of("66000"),
                10))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("방향");
    }

    @Test
    void 유지증거금률_정책이_없으면_분석할_수_없다() {
        assertThatThrownBy(() -> 롱_50퍼센트_분할().analyze(BUDGET, null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("유지증거금률");
    }

    @Test
    void 리스크_예산이_없으면_수량을_구할_수_없다() {
        assertThatThrownBy(() -> 롱_50퍼센트_분할().totalQuantity(null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("리스크 예산");
    }
}
