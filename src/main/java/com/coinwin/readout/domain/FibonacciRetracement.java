package com.coinwin.readout.domain;

import com.coinwin.backtest.domain.Pivot;
import com.coinwin.backtest.domain.PivotKind;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 최근 스윙 하나에 걸친 피보나치 되돌림.
 *
 * <p><b>레벨은 산술이고 그 이상은 아니다.</b> 0.618 은 스윙 폭의 61.8% 지점이라는 뜻이며,
 * 그 자리에서 가격이 되돌아온다는 것은 <b>이 타입이 말하지 않는 별개의 주장</b>이다. 흔히
 * 인용되는 "61.8% 에서 70% 이상 반등한다" 는 근거 데이터가 붙은 것을 찾지 못했고, 이 저장소는
 * 같은 종류의 전제를 7년 15,110봉에서 반증한 이력이 있다({@code docs/adr/021}). 재고 싶으면
 * 백테스트가 그 자리다.
 *
 * <p><b>스윙은 피벗이 정한다.</b> 눈으로 고르면 사람마다 다른 선이 그어지고, 그러면 화면의
 * 레벨과 백테스트의 레벨이 애초에 같은 것이 아니게 된다. {@code PivotDetector} 가 잡은 마지막
 * 고점과 마지막 저점을 쓰므로 <b>대와 같은 스윙 위에 그려진다.</b>
 *
 * <p><b>방향은 어느 쪽이 나중인가로 정해진다.</b> 저점이 먼저고 고점이 나중이면 오른 스윙이고
 * 되돌림은 위에서 아래로 잰다. 반대면 그 반대다. 이것은 관측이지 추세 판정이 아니다.
 *
 * @param low 스윙 저점
 * @param high 스윙 고점
 * @param upward 저점에서 고점으로 간 스윙인가
 * @param levels 되돌림 비율 → 그 비율의 가격. 넣은 순서가 화면 순서다
 */
public record FibonacciRetracement(
        Price low, Price high, boolean upward, Map<BigDecimal, Price> levels) {

    /**
     * 흔히 쓰는 되돌림 비율.
     *
     * <p>0.618 과 0.65 를 함께 두는 이유는 그 둘 사이를 <b>골든 포켓</b>이라 부르며 하나의 띠로
     * 보기 때문이다 — 점이 아니라 구간이라는 것이 이 관습의 요점이고, 그래서 둘 중 하나만
     * 그리면 그 띠가 사라진다.
     */
    private static final List<BigDecimal> RATIOS = List.of(
            new BigDecimal("0.236"),
            new BigDecimal("0.382"),
            new BigDecimal("0.500"),
            new BigDecimal("0.618"),
            new BigDecimal("0.650"),
            new BigDecimal("0.786"));

    /** 골든 포켓의 두 끝. 화면이 이 구간을 하나로 칠할 때 쓴다. */
    public static final BigDecimal POCKET_NEAR = new BigDecimal("0.618");

    public static final BigDecimal POCKET_FAR = new BigDecimal("0.650");

    public FibonacciRetracement {
        DomainValues.required(low, "스윙 저점");
        DomainValues.required(high, "스윙 고점");
        DomainValues.required(levels, "되돌림 레벨");
        levels = Map.copyOf(levels);
    }

    /**
     * 마지막 스윙 고점과 저점으로 되돌림을 만든다.
     *
     * <p><b>둘 중 하나라도 없으면 그리지 않는다.</b> 봉이 모자라거나 한쪽 극값만 잡힌 구간에서
     * 없는 스윙을 지어내면 화면에 아무 뜻 없는 선 여섯 개가 생긴다.
     */
    public static Optional<FibonacciRetracement> over(List<Pivot> pivots) {
        DomainValues.required(pivots, "피벗");
        Optional<Pivot> high = last(pivots, PivotKind.SWING_HIGH);
        Optional<Pivot> low = last(pivots, PivotKind.SWING_LOW);
        if (high.isEmpty() || low.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(between(low.get(), high.get()));
    }

    private static FibonacciRetracement between(Pivot low, Pivot high) {
        boolean upward = low.at().isBefore(high.at());
        return new FibonacciRetracement(
                low.price(), high.price(), upward, levelsOf(low.price(), high.price(), upward));
    }

    /**
     * 각 비율의 가격.
     *
     * <p>오른 스윙에서 되돌림은 <b>고점에서 아래로</b> 잰다 — 0.618 은 고점에서 스윙 폭의
     * 61.8% 만큼 내려온 자리다. 내린 스윙이면 저점에서 위로 같은 만큼이다. 방향을 뒤집어 적으면
     * 같은 0.618 이 전혀 다른 가격을 가리킨다.
     */
    private static Map<BigDecimal, Price> levelsOf(Price low, Price high, boolean upward) {
        BigDecimal span = high.value().subtract(low.value());
        BigDecimal anchor = upward ? high.value() : low.value();
        Map<BigDecimal, Price> levels = new LinkedHashMap<>();
        for (BigDecimal ratio : RATIOS) {
            BigDecimal moved = span.multiply(ratio);
            levels.put(ratio, Price.of(upward ? anchor.subtract(moved) : anchor.add(moved)));
        }
        return levels;
    }

    /** 지금 가격이 골든 포켓 안인가. <b>사실 하나이며 그 다음은 이 타입이 말하지 않는다.</b> */
    public boolean isInGoldenPocket(Price close) {
        DomainValues.required(close, "현재가");
        Price near = levels.get(POCKET_NEAR);
        Price far = levels.get(POCKET_FAR);
        BigDecimal lower = near.value().min(far.value());
        BigDecimal upper = near.value().max(far.value());
        return close.value().compareTo(lower) >= 0 && close.value().compareTo(upper) <= 0;
    }

    private static Optional<Pivot> last(List<Pivot> pivots, PivotKind kind) {
        return pivots.stream()
                .filter(pivot -> pivot.kind() == kind)
                .max(Comparator.comparing(Pivot::at));
    }
}
