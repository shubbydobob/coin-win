package com.coinwin.market.domain;

import com.coinwin.common.domain.InvalidValueException;
import java.util.Arrays;

/**
 * 호가를 몇 단까지 볼 것인가.
 *
 * <p><b>아무 수나 되지 않는다.</b> 거래소가 정해진 값만 받는다 — {@code depth=3} 을 보내면
 * {@code -4021 "3 is not valid depth limit"} 로 거절한다. 실제로 그렇게 한 번 거절당하고 나서
 * 이 타입을 만들었다.
 *
 * <p>그전에는 서비스가 "1 과 20 사이" 로 검사하고 있었다. 그 범위는 우리가 상상한 것이고
 * 거래소가 정한 것이 아니다. 계약 테스트가 5 와 20 만 써서 그 어긋남을 지나갔다 —
 * <b>인메모리 어댑터는 3 도 받아 주기 때문에</b> 두 구현이 다르게 행동해도 스위트가 통과했다.
 * 값을 타입으로 막으면 그 갈라짐 자체가 생기지 않는다.
 *
 * <p>20 을 넘는 값(50·100·500·1000)은 거래소가 받지만 우리가 쓰지 않는다. 더 깊이 가면 실제로
 * 체결될 일 없는 주문이 섞여 불균형이 흐려지고, 20 이 호출 가중치가 낮게 유지되는 상한이기도
 * 하다. 근거: {@code docs/spec/market-watch.md} § 3.2
 */
public record OrderBookDepth(int levels) {

    /** 거래소가 받는 값 중 우리가 쓰는 것. 오름차순으로 둔다. */
    private static final int[] ALLOWED = {5, 10, 20};

    public static final OrderBookDepth DEFAULT = new OrderBookDepth(20);

    public OrderBookDepth {
        if (Arrays.stream(ALLOWED).noneMatch(allowed -> allowed == levels)) {
            throw new InvalidValueException(
                    "호가 단수는 %s 중 하나여야 한다: %d".formatted(Arrays.toString(ALLOWED), levels));
        }
    }

    public static OrderBookDepth of(int levels) {
        return new OrderBookDepth(levels);
    }
}
