package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BollingerBands;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.IchimokuValue;
import com.coinwin.indicator.domain.IndicatorPoint;
import com.coinwin.indicator.domain.Macd;
import com.coinwin.indicator.domain.MacdValue;
import com.coinwin.indicator.domain.MovingAverage;
import com.coinwin.indicator.domain.RelativeStrengthIndex;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.LinkedHashMap;

/**
 * 한 주기의 지표를 <b>봉마다</b> 낸 것. 판독({@link TimeframeReadout})이 마지막 봉 하나를
 * 말한다면 이쪽은 그리기 위한 곡선이다.
 *
 * <p><b>왜 따로 있는가.</b> 화면이 구름을 수평선으로 그리고 있었다 — 판독 응답에 지금 봉의
 * 값밖에 없었기 때문이다. 구름과 밴드는 시간에 따라 움직이는 것이고, 이동평균선은 곡선이
 * 아니면 아무 뜻이 없으며, RSI 는 0~100 축이라 아예 다른 칸이 필요하다.
 *
 * <p><b>어느 지표를 보는가가 이 타입의 유일한 결정이다.</b> 계산은 전부
 * {@code indicator.domain} 이 하고 여기서는 고르고 묶기만 한다. 그 고름이 화면이 아니라
 * 도메인에 있는 이유는, 화면이 고르면 <b>주기마다·화면마다 다른 지표가 뜨게 되고</b> 그러면
 * "세 주기가 같은 말을 하는가" 라는 비교가 성립하지 않기 때문이다.
 *
 * <p><b>지표마다 시작하는 봉이 다르다.</b> 200 이동평균은 200봉째부터, 일목은 77봉째부터
 * 값을 갖는다. 길이를 맞추려고 앞을 채우지 않는다 — 없는 값을 0 이나 첫 값으로 채우면 그것이
 * 지표처럼 보인다. 점마다 시각을 달고 있으므로 그리는 쪽이 알아서 맞춘다.
 *
 * <p><b>봉이 모자라면 그 지표만 빠진다.</b> 300봉 요청에 120봉만 저장돼 있으면 200 이동평균은
 * 없고 나머지는 있다. 하나 때문에 전부를 거절하면 짧은 주기가 아무것도 못 그린다.
 */
public record TimeframeSeries(
        CandleInterval interval,
        CandleSeries candles,
        List<IndicatorPoint<IchimokuValue>> ichimoku,
        List<IndicatorPoint<BollingerValue>> bollinger,
        Map<Integer, List<IndicatorPoint<Price>>> movingAverages,
        List<IndicatorPoint<Percentage>> rsi,
        List<IndicatorPoint<MacdValue>> macd) {

    /**
     * 화면에 놓는 이동평균 구간.
     *
     * <p>20 은 볼린저 중심선과 같은 값이다 — 중복이 아니라 <b>사실</b>이고, 밴드를 끄고 봐도
     * 그 선이 남아야 하므로 따로 낸다. 50·200 은 관습이며 검증한 수가 아니다.
     */
    public static final List<Integer> MA_PERIODS = List.of(20, 50, 200);

    public TimeframeSeries {
        DomainValues.required(interval, "캔들 주기");
        DomainValues.required(candles, "캔들 묶음");
        ichimoku = List.copyOf(ichimoku);
        bollinger = List.copyOf(bollinger);
        movingAverages = Map.copyOf(movingAverages);
        rsi = List.copyOf(rsi);
        macd = List.copyOf(macd);
    }

    /** 캔들에서 다섯 지표를 낸다. 봉이 모자란 지표는 빈 목록이 된다. */
    public static TimeframeSeries over(CandleInterval interval, CandleSeries candles) {
        DomainValues.required(candles, "캔들 묶음");
        Map<Integer, List<IndicatorPoint<Price>>> averages = new LinkedHashMap<>();
        for (int period : MA_PERIODS) {
            averages.put(period, orEmpty(() -> MovingAverage.simple(period).over(candles)));
        }
        return new TimeframeSeries(
                interval,
                candles,
                orEmpty(() -> IchimokuCloud.standard().over(candles)),
                orEmpty(() -> BollingerBands.standard().over(candles)),
                averages,
                orEmpty(() -> RelativeStrengthIndex.standard().over(candles)),
                orEmpty(() -> Macd.standard().over(candles)));
    }

    /**
     * 봉이 모자라면 빈 목록. <b>없는 것과 계산이 틀린 것을 가르지 않는 것처럼 보이지만 다르다</b> —
     * 계산기는 봉이 모자랄 때만 {@code InsufficientCandlesException} 을 던지고, 다른 잘못된
     * 입력은 {@code InvalidIndicatorException} 이라 여기서 잡히지 않는다.
     */
    private static <T> List<T> orEmpty(Supplier<List<T>> calculate) {
        try {
            return calculate.get();
        } catch (com.coinwin.indicator.domain.InsufficientCandlesException e) {
            return List.of();
        }
    }
}
