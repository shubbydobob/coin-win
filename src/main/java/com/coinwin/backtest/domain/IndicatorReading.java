package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.position.domain.Direction;

/**
 * 한 시점의 지표 판정 둘. 필터를 껐을 때도 <b>항상</b> 채워진다.
 *
 * <p>끈 실행과 켠 실행이 같은 값을 기록해야 온오프 비교가 성립하고, 실제 매매 기록
 * ({@code MarketContext})도 두 판정을 요구한다.
 *
 * @param ichimoku 종가가 구름의 어디에 있는가
 * @param bollinger 종가가 볼린저 밴드의 어디에 있는가
 */
public record IndicatorReading(BandPosition ichimoku, BandPosition bollinger) {

    public IndicatorReading {
        DomainValues.required(ichimoku, "일목 판정");
        DomainValues.required(bollinger, "볼린저 판정");
    }

    /**
     * 두 지표가 이 방향에 반대하지 않는가.
     *
     * <p>구름 아래에서 롱, 구름 위에서 숏은 추세를 거스른다. 볼린저 상단 밖에서 롱, 하단 밖에서
     * 숏은 이미 늘어난 쪽으로 더 가는 것이다. <b>둘 다 "반대하지 않으면" 통과</b>이고 적극적
     * 동의를 요구하지 않는다 — 대 반전이 진입 근거이고 지표는 필터이지 신호가 아니다.
     */
    public boolean agreesWith(Direction direction) {
        DomainValues.required(direction, "진입 방향");
        return switch (direction) {
            case LONG -> ichimoku != BandPosition.BELOW && bollinger != BandPosition.ABOVE;
            case SHORT -> ichimoku != BandPosition.ABOVE && bollinger != BandPosition.BELOW;
        };
    }
}
