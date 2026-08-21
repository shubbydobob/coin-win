package com.coinwin.ai.domain;

import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;

/**
 * 모델이 읽어 온 분할 진입 한 회차. <b>두 칸 다 비어 있을 수 있다.</b>
 *
 * <p>{@code position.domain} 의 {@code PlannedEntry} 와 모양이 같지만 같은 타입이 아니다.
 * 그쪽은 생성 시점에 null 을 거부하므로 "가격을 못 읽었다" 는 상태를 표현할 수 없다.
 * 못 읽은 것을 표현할 수 없으면 어딘가에서 채워 넣게 되고, 그것이 정확히 하지 않기로 한 일이다.
 */
public record DraftedEntry(Price price, Percentage allocation) {

    boolean isBlank() {
        return price == null || allocation == null;
    }
}
