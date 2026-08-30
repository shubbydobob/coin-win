package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;

/**
 * 밴드 안에서 <b>어디쯤인가.</b> 하단이 0, 상단이 1 이다.
 *
 * <p><b>{@link BandPosition} 이 답하지 못하는 것을 답한다.</b> 위치는 밖인지 안인지까지만
 * 말하는데, 밴드 안에서도 하단에 붙어 있는 것과 중심선 바로 아래인 것은 전혀 다른 자리다.
 * 그리고 그 차이가 분할 진입에서 첫 칸을 어디 둘지를 가른다.
 *
 * <p><b>0~1 을 벗어난다.</b> 밖으로 나가면 음수이거나 1 을 넘고, 그것을 잘라 내지 않는다 —
 * 얼마나 벗어났는지가 밴드를 보는 이유의 절반이다.
 *
 * <p><b>구름에도 쓴다.</b> 볼린저 전용 이름(%b)을 쓰지 않은 것이 그 때문이다. 밴드가
 * {@link PriceBand} 하나로 공유되므로 이 값도 하나여야 한다.
 */
public record BandRatio(BigDecimal value) {

    private static final int SCALE = 4;

    private static final String LABEL = "밴드 안 위치";

    public BandRatio {
        value = DomainValues.scaled(value, SCALE, LABEL);
    }
}
