package com.coinwin.position.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import org.junit.jupiter.api.Test;

/**
 * 손절가가 청산가 너머면 손절 주문이 작동하기 전에 청산이 먼저 일어난다.
 *
 * <p>두 테스트 모두 <b>부분 체결 상태에서만</b> 위반이 성립하도록 잡았다. 전량 체결 기준으로만
 * 검사하는 구현은 이 계획을 통과시키는데, 실제로는 1차 체결 직후에 이미 청산이 손절보다 앞선다.
 */
class StopBeyondLiquidationTest {

    private static final MaintenanceMarginPolicy MMR04 =
            FixedMaintenanceMarginPolicy.btcUsdtApproximation();
    private static final RiskBudget BUDGET = RiskBudget.of("800", "2");

    @Test
    void 롱_1차_체결_상태의_청산가가_손절가보다_먼저_닿으면_거부된다() {
        // 3배 → 계수 1 - 1/3 + 0.4% = 0.670666...
        // 1차 평단 60,000 → 청산가 40,240 (손절 40,000 보다 먼저 닿는다)
        // 전량 평단 59,000 → 청산가 39,569.33 (여기서만 보면 안전해 보인다)
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("40000"),
                Price.of("66000"),
                3);

        assertThatThrownBy(() -> plan.analyze(BUDGET, MMR04))
                .isInstanceOf(StopBeyondLiquidationException.class)
                .hasMessageContaining("1건 체결")
                .hasMessageContaining("40240.00");
    }

    @Test
    void 숏_1차_체결_상태의_청산가가_손절가보다_먼저_닿으면_거부된다() {
        // 3배 → 계수 1 + 1/3 - 0.4% = 1.329333...
        // 1차 평단 60,000 → 청산가 79,760 (손절 90,000 보다 먼저 닿는다)
        PositionPlan plan = new PositionPlan(
                Direction.SHORT,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("62000", "50")),
                Price.of("90000"),
                Price.of("55000"),
                3);

        assertThatThrownBy(() -> plan.analyze(BUDGET, MMR04))
                .isInstanceOf(StopBeyondLiquidationException.class)
                .hasMessageContaining("1건 체결")
                .hasMessageContaining("79760.00");
    }

    @Test
    void 손절가가_모든_체결_상태의_청산가_안쪽이면_통과한다() {
        PositionPlan plan = new PositionPlan(
                Direction.LONG,
                EntryLadder.of(PlannedEntry.of("60000", "50"), PlannedEntry.of("58000", "50")),
                Price.of("56000"),
                Price.of("66000"),
                10);

        assertThatCode(() -> plan.analyze(BUDGET, MMR04)).doesNotThrowAnyException();
    }
}
