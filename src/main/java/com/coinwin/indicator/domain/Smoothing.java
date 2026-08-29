package com.coinwin.indicator.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 세 가지 평활. <b>세 지표가 이것을 나눠 쓴다</b> — 이동평균 · RSI · MACD.
 *
 * <p><b>세 가지가 서로 다른 것이 이 파일의 요점이다.</b> 이름이 전부 "평균" 이라 같은 것처럼
 * 들리지만 결과가 다르고, 잘못 고르면 값이 그럴듯하게 틀린다.
 *
 * <ul>
 *   <li>{@code sma} — 구간의 산술평균. 볼린저 중심선이 이것이다.
 *   <li>{@code ema} — 지수 가중, {@code alpha = 2 / (n + 1)}. MACD 가 이것이다.
 *   <li>{@code rma} — 와일더 평활, {@code alpha = 1 / n}. <b>RSI 와 ATR 이 이것이다.</b>
 * </ul>
 *
 * <p><b>RSI 를 EMA 로 짜면 값이 다르다.</b> 같은 14 라도 {@code alpha} 가 2/15 대 1/14 이고,
 * 이것은 트레이딩뷰 원문이 {@code ta.rma} 를 쓴다고 못 박은 자리다:
 *
 * <pre>{@code
 * up = rma(max(change(src), 0), len)
 * down = rma(-min(change(src), 0), len)
 * }</pre>
 *
 * <p><b>두 지수 평활 모두 첫 값을 단순평균으로 앉힌다.</b> 트레이딩뷰가 그렇게 하고, 그러지
 * 않으면 초반 수십 봉이 어긋난 채 수렴한다 — 화면에서는 "조금 다르다" 로만 보인다.
 *
 * <p>출력 길이는 언제나 {@code 입력 − (period − 1)} 이다. 워밍업 구간을 채워서 돌려주지
 * 않는다 — 없는 값을 0 이나 첫 값으로 채우면 그것이 지표처럼 보인다.
 */
final class Smoothing {

    /** 나눗셈 정밀도. 지표는 표시 전에 반올림하므로 여기서는 넉넉히 둔다. */
    static final MathContext MC = new MathContext(20);

    private Smoothing() {
    }

    /** 단순이동평균. */
    static List<BigDecimal> sma(List<BigDecimal> values, int period) {
        check(values, period);
        List<BigDecimal> out = new ArrayList<>(values.size() - period + 1);
        BigDecimal window = sum(values, 0, period);
        out.add(window.divide(BigDecimal.valueOf(period), MC));
        for (int i = period; i < values.size(); i++) {
            window = window.add(values.get(i)).subtract(values.get(i - period));
            out.add(window.divide(BigDecimal.valueOf(period), MC));
        }
        return out;
    }

    /** 지수이동평균. {@code alpha = 2 / (period + 1)}. */
    static List<BigDecimal> ema(List<BigDecimal> values, int period) {
        return exponential(values, period, BigDecimal.valueOf(2).divide(
                BigDecimal.valueOf(period + 1L), MC));
    }

    /** 와일더 평활. {@code alpha = 1 / period}. RSI 와 ATR 이 쓴다. */
    static List<BigDecimal> rma(List<BigDecimal> values, int period) {
        return exponential(values, period, BigDecimal.ONE.divide(
                BigDecimal.valueOf(period), MC));
    }

    /**
     * 지수 평활 공통부. <b>첫 값은 단순평균이고 그 뒤로 재귀한다.</b>
     *
     * <p>{@code 다음 = alpha × 지금 + (1 − alpha) × 이전}.
     */
    private static List<BigDecimal> exponential(
            List<BigDecimal> values, int period, BigDecimal alpha) {
        check(values, period);
        BigDecimal keep = BigDecimal.ONE.subtract(alpha);
        List<BigDecimal> out = new ArrayList<>(values.size() - period + 1);
        BigDecimal previous = sum(values, 0, period).divide(BigDecimal.valueOf(period), MC);
        out.add(previous);
        for (int i = period; i < values.size(); i++) {
            previous = alpha.multiply(values.get(i), MC).add(keep.multiply(previous, MC), MC);
            out.add(previous);
        }
        return out;
    }

    private static BigDecimal sum(List<BigDecimal> values, int from, int count) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = from; i < from + count; i++) {
            total = total.add(values.get(i));
        }
        return total;
    }

    private static void check(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            throw new InsufficientCandlesException("평활", period, values.size());
        }
    }
}
