package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BollingerBands;
import com.coinwin.indicator.domain.IchimokuCloud;
import com.coinwin.indicator.domain.Macd;
import com.coinwin.indicator.domain.RelativeStrengthIndex;
import com.coinwin.market.domain.CandleSeries;

/**
 * 한 주기에서 지표가 말하는 것.
 *
 * <p><b>지표마다 자기 묶음을 갖는다.</b> 처음에는 열 칸이 한 줄에 늘어서 있었고, 그러면
 * 「구름 상단」 과 「밴드 상단」 이 같은 높이에 놓여 <b>어느 것이 어느 지표의 값인지가
 * 이름에만 남는다.</b> 칸을 늘릴수록 그 자리가 나빠진다.
 *
 * <p><b>계산기를 여기서 부른다.</b> 이력이 필요한 값들(밴드폭 순위·밴드 걷기·교차 이후 경과
 * 봉·후행스팬)은 마지막 값 하나로는 나오지 않는다 — 부르는 쪽이 목록을 넘겨 주는 모양이면
 * "어느 창에서 잰 것인가" 가 호출부마다 달라질 수 있다.
 *
 * <p><b>판정하지 않는다.</b> 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 예측을 7년 15,110봉에서 반증했다({@code docs/adr/021}).
 *
 * <p><b>무리를 합친 수를 만들지 않는다.</b> 일목·MACD·이동평균은 추세를, 볼린저는 되돌림을
 * 재고 둘은 같은 사실에 반대 뜻을 붙인다 — {@link IndicatorFamily} 가 그 이유를 갖고 있다.
 *
 * @param ichimoku 구름과 두 선, 그리고 후행스팬
 * @param bollinger 밴드와 그 폭의 순위
 * @param rsi 50 으로 접기 전의 값과 그 움직임
 * @param macd 시그널 대비와 영선 대비, 그리고 그 자리가 이어진 봉 수
 * @param movingAverage 20 과 200 이 벌어진 정도
 */
public record IndicatorReadout(
        IchimokuReadout ichimoku,
        BollingerReadout bollinger,
        RsiReadout rsi,
        MacdReadout macd,
        MovingAverageReadout movingAverage) {

    public IndicatorReadout {
        DomainValues.required(ichimoku, "일목");
        DomainValues.required(bollinger, "볼린저");
        DomainValues.required(rsi, "RSI");
        DomainValues.required(macd, "MACD");
        DomainValues.required(movingAverage, "이동평균");
    }

    /**
     * 캔들에서 다섯 지표를 읽는다.
     *
     * <p><b>ATR 을 함께 받는 이유</b>는 거리를 그 단위로 재기 때문이다. 여기서 다시 계산하면
     * 판독의 다른 값들이 쓰는 ATR 과 갈라질 수 있고, 그러면 같은 화면의 두 수가 다른 변동성을
     * 기준으로 삼는다.
     *
     * <p><b>봉이 모자라면 던진다.</b> 일목이 가장 긴 워밍업을 갖고 있어 그쪽이 먼저 걸리며,
     * 여기서 같은 검사를 또 하지 않는다 — 몇 봉이 필요한지는 계산기가 안다. 200 이동평균만은
     * 없어도 나머지가 성립하므로 그 하나만 빈다.
     */
    public static IndicatorReadout over(CandleSeries series, Money atr) {
        DomainValues.required(series, "캔들 묶음");
        DomainValues.required(atr, "ATR");
        IchimokuCloud cloud = IchimokuCloud.standard();
        Price close = series.candles().getLast().close();
        return new IndicatorReadout(
                IchimokuReadout.of(
                        cloud.over(series).getLast().value(),
                        close,
                        atr,
                        cloud.laggingSpanGap(series)),
                BollingerReadout.over(BollingerBands.standard().over(series), series),
                RsiReadout.over(RelativeStrengthIndex.standard().over(series)),
                MacdReadout.over(Macd.standard().over(series), atr),
                MovingAverageReadout.over(series, atr));
    }
}
