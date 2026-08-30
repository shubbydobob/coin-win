package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuValue;

/**
 * 한 주기에서 지표가 말하는 것.
 *
 * <p><b>지표마다 자기 묶음을 갖는다.</b> 처음에는 열 칸이 한 줄에 늘어서 있었고, 그러면
 * 「구름 상단」 과 「밴드 상단」 이 같은 높이에 놓여 <b>어느 것이 어느 지표의 값인지가
 * 이름에만 남는다.</b> 칸을 늘릴수록 그 자리가 나빠진다.
 *
 * <p><b>판정하지 않는다.</b> 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 예측을 7년 15,110봉에서 반증했다({@code docs/adr/021}). 그래서 여기 담기는 것은 위치와
 * 값까지이고, 방향은 사람이 정한다.
 *
 * <p><b>둘을 합친 수를 만들지 않는다.</b> 일목은 추세를, 볼린저는 되돌림을 재고 둘은 같은
 * 사실에 반대 뜻을 붙인다 — {@link IndicatorFamily} 가 그 이유를 갖고 있다.
 *
 * @param ichimoku 구름과 두 선
 * @param bollinger 밴드
 */
public record IndicatorReadout(IchimokuReadout ichimoku, BollingerReadout bollinger) {

    public IndicatorReadout {
        DomainValues.required(ichimoku, "일목");
        DomainValues.required(bollinger, "볼린저");
    }

    /**
     * 두 지표 값을 지금 가격과 변동성 기준으로 읽는다.
     *
     * <p><b>ATR 을 함께 받는 이유</b>는 거리를 그 단위로 재기 때문이다. 여기서 다시 계산하면
     * 판독의 다른 값들이 쓰는 ATR 과 갈라질 수 있고, 그러면 같은 화면의 두 수가 다른 변동성을
     * 기준으로 삼는다.
     */
    public static IndicatorReadout of(
            IchimokuValue ichimoku, BollingerValue bollinger, Price close, Money atr) {
        return new IndicatorReadout(
                IchimokuReadout.of(ichimoku, close, atr), BollingerReadout.of(bollinger, close));
    }
}
