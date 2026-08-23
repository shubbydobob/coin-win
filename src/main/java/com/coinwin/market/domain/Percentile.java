package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 표본 안에서 현재값이 차지하는 위치. 0 이 최저, 1 이 최고다.
 *
 * <p><b>배수로 말하지 않는 이유가 이 타입의 존재 이유다.</b> "평소 대비 몇 배" 는 펀딩비에서
 * 무너진다 — 평소가 {@code +0.001%} 인데 지금이 {@code -0.05%} 라면 배수는 {@code -50} 이고
 * 그 수는 아무 뜻이 없다. 0 근처를 지날 때는 무한대로 튄다. 위치는 부호에도 이상치에도
 * 무너지지 않고, "지금이 평소와 얼마나 다른가" 에 직접 답한다.
 *
 * <p><b>동점은 절반만 아래로 센다.</b> 실제 펀딩비는 연속으로 정확히 같은 값이 나온다
 * ({@code 0.00010000} 이 이어서 두 번). 작은 것만 세면 하위 0%, 작거나 같은 것을 세면 상위 0%
 * 가 되어 <b>같은 입력에서 정반대 답</b>이 나온다. 중간순위는 그 둘의 가운데를 준다.
 *
 * <p>근거: {@code docs/spec/market-watch-design.md} § 2.1
 */
public record Percentile(BigDecimal value) {

    /** 표시 자릿수. 0.0001 단위면 표본 1만 개까지 구분된다. */
    private static final int SCALE = 4;

    /** 양 끝 이만큼이 이상치다. <b>이 수는 여기에만 있다</b> — 화면이 복창하지 않는다. */
    private static final BigDecimal EDGE = new BigDecimal("0.05");

    private static final BigDecimal HALF = new BigDecimal("0.5");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Percentile {
        value = DomainValues.scaled(value, SCALE, "위치");
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidValueException("위치는 0 과 1 사이여야 한다: " + value);
        }
    }

    /**
     * 표본 안에서 현재값의 위치.
     *
     * <pre>
     * 위치 = ( 작은 표본 수 + 0.5 × 같은 표본 수 ) / 전체 표본 수
     * </pre>
     */
    public static Percentile of(BigDecimal current, List<BigDecimal> samples) {
        DomainValues.required(current, "현재값");
        DomainValues.required(samples, "표본");
        if (samples.isEmpty()) {
            throw new InvalidValueException("표본이 비면 위치를 낼 수 없다");
        }
        BigDecimal rank = below(current, samples).add(HALF.multiply(equal(current, samples)));
        return new Percentile(
                rank.divide(BigDecimal.valueOf(samples.size()), SCALE, RoundingMode.HALF_UP));
    }

    /** 위쪽에서부터의 비율. 위치 0.88 은 상위 12% 다. */
    public Percentage topPercent() {
        return Percentage.of(BigDecimal.ONE.subtract(value).multiply(HUNDRED));
    }

    /** 양 끝 5% 안인가. 위쪽이든 아래쪽이든 평소와 다르다는 뜻은 같다. */
    public boolean isOutlier() {
        return value.compareTo(EDGE) < 0 || value.compareTo(BigDecimal.ONE.subtract(EDGE)) > 0;
    }

    private static BigDecimal below(BigDecimal current, List<BigDecimal> samples) {
        return count(samples, sample -> sample.compareTo(current) < 0);
    }

    private static BigDecimal equal(BigDecimal current, List<BigDecimal> samples) {
        return count(samples, sample -> sample.compareTo(current) == 0);
    }

    private static BigDecimal count(
            List<BigDecimal> samples, java.util.function.Predicate<BigDecimal> matches) {
        return BigDecimal.valueOf(samples.stream().filter(matches).count());
    }
}
