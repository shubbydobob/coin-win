package com.coinwin.projection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompoundTargetTest {

    private static CompoundTarget 목표(String 월목표, int 개월, String 레버리지, int 월거래) {
        return new CompoundTarget(
                Money.of("800"), Percentage.of(월목표), 개월,
                new TradingCost(
                        Percentage.of("0.05"), Percentage.of("0.02"),
                        new BigDecimal(레버리지), Percentage.of("100"), 월거래));
    }

    @Test
    void 월별_자산은_개월보다_한_점_많고_첫_점은_시작_자산이다() {
        assertThat(목표("5", 12, "10", 20).monthlyEquity())
                .hasSize(13)
                .first()
                .isEqualTo(Money.of("800"));
    }

    @Test
    void 목표를_지키면_최종_자산은_시작_자산에_복리를_곱한_것이다() {
        // 800 × 1.05^12 = 1436.685...
        assertThat(목표("5", 12, "10", 20).finalEquity().value())
                .isEqualByComparingTo("1436.69");
    }

    /** 사람이 "얼마 버나" 로 묻는 수. 화면이 뺄셈하지 않도록 도메인이 낸다. */
    @Test
    void 순이익은_최종_자산에서_시작_자산을_뺀_것이다() {
        assertThat(목표("5", 12, "10", 20).totalProfit().value()).isEqualByComparingTo("636.69");
    }

    /**
     * 순이익만 비용 옆에 놓으면 "636 을 버는데 3,649 를 낸다" 로 읽혀 손실처럼 보인다.
     * 번 돈이 있어야 셋이 닫힌다 — 4,286 을 벌어 3,649 를 내고 636 이 남는다.
     */
    @Test
    void 번_돈은_남는_돈에_낸_돈을_더한_것이다() {
        CompoundTarget 목표 = 목표("5", 12, "10", 20);

        assertThat(목표.grossProfit())
                .isEqualTo(목표.totalProfit().plus(목표.totalCost()));
        assertThat(목표.grossProfit().value()).isEqualByComparingTo("4286.08");
    }

    /** 이 화면이 존재하는 이유의 절반. 월 5% 는 열두 달이면 60% 가 아니다. */
    @Test
    void 총_수익률은_단리가_아니라_복리다() {
        assertThat(목표("5", 12, "10", 20).totalReturn().value())
                .isEqualByComparingTo("79.5856");
    }

    @Test
    void 한_달만_두면_총_수익률이_월_목표와_같다() {
        assertThat(목표("5", 1, "10", 20).totalReturn().value()).isEqualByComparingTo("5.0000");
    }

    @Test
    void 총_거래_수는_월_거래_수에_개월을_곱한_것이다() {
        assertThat(목표("5", 12, "10", 20).totalTrades()).isEqualTo(240);
    }

    @Test
    void 명목은_시작_자산에_레버리지를_곱한_것이다() {
        assertThat(목표("5", 12, "10", 20).notional().value()).isEqualByComparingTo("8000.00");
    }

    /**
     * <b>이 계산기의 요점.</b> 같은 목표·같은 기간이라도 레버리지를 올리면 거래소에 내는 돈이
     * 그만큼 늘어난다. 최종 자산은 한 푼도 달라지지 않는데 비용만 커진다 — 목표가 순수익이기
     * 때문이다.
     */
    @Test
    void 레버리지를_올리면_최종_자산은_그대로인데_총_비용만_커진다() {
        CompoundTarget 저배율 = 목표("5", 12, "2", 20);
        CompoundTarget 고배율 = 목표("5", 12, "20", 20);

        assertThat(고배율.finalEquity()).isEqualTo(저배율.finalEquity());
        assertThat(고배율.totalCost().isGreaterThan(저배율.totalCost())).isTrue();
    }

    @Test
    void 비용이_없으면_총_비용도_0_이다() {
        CompoundTarget 무비용 = new CompoundTarget(
                Money.of("800"), Percentage.of("5"), 12,
                new TradingCost(
                        Percentage.of("0"), Percentage.of("0"), BigDecimal.TEN,
                        Percentage.of("100"), 20));

        assertThat(무비용.totalCost().value()).isEqualByComparingTo("0.00");
    }

    /** 비용은 목표를 깎지 않는다. 목표는 비용을 낸 뒤에 남는 수익이라고 정의했다. */
    @Test
    void 비용이_달라져도_월별_자산_곡선은_같다() {
        assertThat(목표("5", 6, "2", 20).monthlyEquity())
                .isEqualTo(목표("5", 6, "20", 20).monthlyEquity());
    }

    @Test
    void 시작_자산이_0_이하면_거부한다() {
        assertThatThrownBy(() -> new CompoundTarget(
                Money.of("0"), Percentage.of("5"), 12,
                new TradingCost(
                        Percentage.of("0.05"), Percentage.of("0.02"), BigDecimal.TEN,
                        Percentage.of("100"), 20)))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("시작 자산");
    }

    @Test
    void 월_목표가_0_이면_거부한다() {
        assertThatThrownBy(() -> 목표("0", 12, "10", 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("월 목표 수익률");
    }

    @Test
    void 기간이_0_개월이면_거부한다() {
        assertThatThrownBy(() -> 목표("5", 0, "10", 20))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("기간(개월)");
    }

    /** 값은 각각 멀쩡한데 곱이 상한을 넘는다. 400 이 아니라 422 인 자리다. */
    @Test
    void 총_거래_수가_상한을_넘으면_조건으로_성립하지_않는다() {
        assertThatThrownBy(() -> 목표("5", 120, "10", 100))
                .isInstanceOf(InvalidProjectionException.class)
                .hasMessageContaining("총 거래 수");
    }

    @Test
    void 거래_비용이_없으면_거부한다() {
        assertThatThrownBy(() -> new CompoundTarget(
                Money.of("800"), Percentage.of("5"), 12, null))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("거래 비용");
    }
}
