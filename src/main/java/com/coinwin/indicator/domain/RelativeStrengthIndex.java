package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * RSI. 오른 폭과 내린 폭의 비를 0~100 으로 옮긴 것이다.
 *
 * <p><b>정의는 트레이딩뷰 원문에서 확정했다</b>({@code STD;RSI/24.0}). 일목 변위를 한 칸
 * 틀렸다가 같은 방법으로 잡은 전례가 있어 값을 눈으로 맞추지 않고 소스를 읽었다:
 *
 * <pre>{@code
 * up   = rma(max(change(src), 0), len)
 * down = rma(-min(change(src), 0), len)
 * rsi  = down == 0 ? 100 : up == 0 ? 0 : 100 - (100 / (1 + up / down))
 * }</pre>
 *
 * <p><b>평활은 RMA 이지 EMA 가 아니다.</b> 같은 14 라도 {@code alpha} 가 1/14 대 2/15 이고,
 * EMA 로 짜면 값이 조금씩 다르면서 그럴듯하게 보인다. {@link Smoothing} 이 셋을 갈라 둔 이유다.
 *
 * <p><b>두 예외를 그대로 옮겼다.</b> 내린 폭이 하나도 없으면 100, 오른 폭이 하나도 없으면 0 이다.
 * 이것을 빼면 {@code up / down} 이 0 으로 나누기가 된다 — 계산식만 보고 옮기면 놓치는 자리이고,
 * 원문에 명시돼 있다.
 *
 * <p><b>변화량은 종가끼리의 차다.</b> 그래서 첫 봉에는 변화가 없고, 봉 {@code n} 개에서 변화는
 * {@code n − 1} 개다. RSI 의 첫 값은 결국 {@code period + 1} 번째 봉에 놓인다.
 *
 * @param period 구간. 트레이딩뷰 기본 14
 */
public record RelativeStrengthIndex(int period) {

    private static final String NAME = "RSI";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public RelativeStrengthIndex {
        DomainValues.atLeast(period, 2, "RSI 기간");
    }

    /** 트레이딩뷰 기본 설정 — 14봉. */
    public static RelativeStrengthIndex standard() {
        return new RelativeStrengthIndex(14);
    }

    /** 이 지표가 값을 내기 시작하려면 봉이 몇 개 필요한가. */
    public int warmup() {
        return period + 1;
    }

    /**
     * 워밍업을 뺀 모든 시점의 RSI.
     *
     * @throws InsufficientCandlesException 봉이 {@link #warmup()} 개 미만인 경우
     */
    public List<IndicatorPoint<Percentage>> over(CandleSeries series) {
        DomainValues.required(series, "캔들 묶음");
        List<Candle> candles = series.candles();
        if (candles.size() < warmup()) {
            throw new InsufficientCandlesException(NAME, warmup(), candles.size());
        }

        List<BigDecimal> smoothedUp = Smoothing.rma(changes(candles, true), period);
        List<BigDecimal> smoothedDown = Smoothing.rma(changes(candles, false), period);

        List<IndicatorPoint<Percentage>> out = new ArrayList<>(smoothedUp.size());
        for (int i = 0; i < smoothedUp.size(); i++) {
            // 변화 i 는 봉 i+1 에서 생겼고, 평활은 앞 period−1 개를 먹는다.
            Candle at = candles.get(i + period);
            out.add(new IndicatorPoint<>(at.openTime(), value(smoothedUp.get(i), smoothedDown.get(i))));
        }
        return out;
    }

    /**
     * 종가 변화의 한쪽만. {@code up} 이면 오른 폭, 아니면 내린 폭(양수)이다.
     *
     * <p>{@code max(change, 0)} 과 {@code -min(change, 0)} 을 한 자리에 둔 것이다 — 둘을 따로
     * 순회하면 같은 뺄셈을 두 번 하고, 그때 한쪽 부호만 고치는 실수가 생긴다.
     */
    private static List<BigDecimal> changes(List<Candle> candles, boolean up) {
        List<BigDecimal> out = new ArrayList<>(candles.size() - 1);
        for (int i = 1; i < candles.size(); i++) {
            BigDecimal change = close(candles, i).subtract(close(candles, i - 1));
            boolean wanted = up ? change.signum() > 0 : change.signum() < 0;
            out.add(wanted ? change.abs() : BigDecimal.ZERO);
        }
        return out;
    }

    private static BigDecimal close(List<Candle> candles, int index) {
        return candles.get(index).close().value();
    }

    /** 원문의 세 갈래를 그대로. 두 예외가 없으면 0 으로 나눈다. */
    private static Percentage value(BigDecimal up, BigDecimal down) {
        if (down.signum() == 0) {
            return Percentage.of(HUNDRED);
        }
        if (up.signum() == 0) {
            return Percentage.of(BigDecimal.ZERO);
        }
        BigDecimal ratio = up.divide(down, Smoothing.MC);
        return Percentage.of(HUNDRED.subtract(
                HUNDRED.divide(BigDecimal.ONE.add(ratio), Smoothing.MC)));
    }
}
