package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.Pivot;
import com.coinwin.backtest.domain.PivotKind;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FibonacciRetracementTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static Pivot 피벗(int 시각, String 가격, PivotKind kind) {
        Instant at = T0.plusSeconds(시각 * 3600L);
        return new Pivot(at, at.plusSeconds(3600), Price.of(new BigDecimal(가격)), kind);
    }

    private static Price 가격(String value) {
        return Price.of(new BigDecimal(value));
    }

    /**
     * <b>오른 스윙에서 되돌림은 고점에서 아래로 잰다.</b> 0.618 은 고점에서 스윙 폭의 61.8%
     * 만큼 내려온 자리다. 방향을 뒤집어 적으면 같은 0.618 이 전혀 다른 가격을 가리킨다.
     */
    @Test
    void 오른_스윙은_고점에서_아래로_잰다() {
        List<Pivot> pivots = List.of(
                피벗(0, "60000", PivotKind.SWING_LOW),
                피벗(10, "80000", PivotKind.SWING_HIGH));

        FibonacciRetracement fib = FibonacciRetracement.over(pivots).orElseThrow();

        assertThat(fib.upward()).isTrue();
        // 폭 20,000 · 0.618 = 12,360 → 80,000 − 12,360
        assertThat(fib.levels().get(new BigDecimal("0.618"))).isEqualTo(가격("67640.00"));
        assertThat(fib.levels().get(new BigDecimal("0.500"))).isEqualTo(가격("70000.00"));
    }

    /** 내린 스윙은 저점에서 위로 잰다. 같은 비율이 반대 방향을 가리킨다. */
    @Test
    void 내린_스윙은_저점에서_위로_잰다() {
        List<Pivot> pivots = List.of(
                피벗(0, "80000", PivotKind.SWING_HIGH),
                피벗(10, "60000", PivotKind.SWING_LOW));

        FibonacciRetracement fib = FibonacciRetracement.over(pivots).orElseThrow();

        assertThat(fib.upward()).isFalse();
        assertThat(fib.levels().get(new BigDecimal("0.618"))).isEqualTo(가격("72360.00"));
        assertThat(fib.levels().get(new BigDecimal("0.500"))).isEqualTo(가격("70000.00"));
    }

    /**
     * <b>골든 포켓은 점이 아니라 띠다.</b> 0.618 과 0.65 를 함께 두는 이유가 그것이고,
     * 둘 중 하나만 그리면 그 띠가 사라진다.
     */
    @Test
    void 골든_포켓은_두_레벨_사이_구간이다() {
        List<Pivot> pivots = List.of(
                피벗(0, "60000", PivotKind.SWING_LOW),
                피벗(10, "80000", PivotKind.SWING_HIGH));

        FibonacciRetracement fib = FibonacciRetracement.over(pivots).orElseThrow();

        // 0.618 → 67,640 · 0.650 → 67,000. 그 사이가 포켓이다.
        assertThat(fib.isInGoldenPocket(가격("67300"))).isTrue();
        assertThat(fib.isInGoldenPocket(가격("67640"))).isTrue();
        assertThat(fib.isInGoldenPocket(가격("67000"))).isTrue();
        assertThat(fib.isInGoldenPocket(가격("68000"))).isFalse();
        assertThat(fib.isInGoldenPocket(가격("66000"))).isFalse();
    }

    /**
     * <b>한쪽 극값만 있으면 그리지 않는다.</b> 없는 스윙을 지어내면 화면에 아무 뜻 없는 선
     * 여섯 개가 생긴다.
     */
    @Test
    void 스윙_한쪽이_없으면_비어_있다() {
        assertThat(FibonacciRetracement.over(List.of())).isEmpty();
        assertThat(FibonacciRetracement.over(List.of(피벗(0, "60000", PivotKind.SWING_LOW))))
                .isEmpty();
    }

    /** 여섯 비율을 전부 낸다. 하나를 빠뜨리면 화면이 그 자리를 조용히 건너뛴다. */
    @Test
    void 여섯_비율을_모두_낸다() {
        Optional<FibonacciRetracement> fib = FibonacciRetracement.over(List.of(
                피벗(0, "60000", PivotKind.SWING_LOW),
                피벗(10, "80000", PivotKind.SWING_HIGH)));

        assertThat(fib.orElseThrow().levels()).hasSize(6)
                .containsKeys(new BigDecimal("0.236"), new BigDecimal("0.382"),
                        new BigDecimal("0.500"), new BigDecimal("0.618"),
                        new BigDecimal("0.650"), new BigDecimal("0.786"));
    }

    /**
     * <b>가장 최근 스윙을 쓴다.</b> 옛 고점에 걸린 되돌림은 지금 가격과 아무 관계가 없고,
     * 그럼에도 선은 그럴듯하게 그려진다.
     */
    @Test
    void 가장_최근_고점과_저점을_쓴다() {
        List<Pivot> pivots = List.of(
                피벗(0, "50000", PivotKind.SWING_LOW),
                피벗(5, "90000", PivotKind.SWING_HIGH),
                피벗(10, "60000", PivotKind.SWING_LOW),
                피벗(20, "80000", PivotKind.SWING_HIGH));

        FibonacciRetracement fib = FibonacciRetracement.over(pivots).orElseThrow();

        assertThat(fib.low()).isEqualTo(가격("60000.00"));
        assertThat(fib.high()).isEqualTo(가격("80000.00"));
    }
}
