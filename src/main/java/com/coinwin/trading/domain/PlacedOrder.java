package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import java.time.Instant;
import java.util.Optional;

/**
 * 실제로 낸 주문. <b>{@link OrderIntent} 와 다른 타입인 것이 요점이다.</b>
 *
 * <p>의도는 전략이 만들고 이것은 브로커만 만든다. 한 타입으로 두면 "내려고 했다" 와 "냈다"
 * 가 같은 값이 되고, 거부되거나 실패한 주문이 기록에서 낸 것처럼 보인다.
 *
 * @param id 브로커가 정한 식별자. 취소할 때 이것으로 가리킨다
 * @param intent 이 주문이 무엇을 하려던 것인가
 * @param fillPrice 체결가. <b>트리거 주문은 비어 있다</b> — 걸려만 있고 아직 체결되지 않았다.
 *     0 으로 채우면 "0 원에 체결됐다" 가 되고 그 수가 손익 계산에 그대로 들어간다
 * @param at 브로커가 받아들인 시각
 * @param mode 어느 모드에서 낸 것인가. <b>기록의 모든 줄에 붙는다</b> — 장부 기록과 실계좌
 *     기록이 한 표에 섞이면 그 표는 아무 말도 하지 않는다
 */
public record PlacedOrder(
        OrderId id,
        OrderIntent intent,
        Optional<Price> fillPrice,
        Instant at,
        TradingMode mode) {

    public PlacedOrder {
        DomainValues.required(id, "주문 식별자");
        DomainValues.required(intent, "주문 의도");
        DomainValues.required(fillPrice, "체결가");
        DomainValues.required(at, "접수 시각");
        DomainValues.required(mode, "모드");
        assertFillMatchesKind(intent, fillPrice);
    }

    /** 걸려만 있고 아직 체결되지 않았는가. */
    public boolean resting() {
        return fillPrice.isEmpty();
    }

    /**
     * 시장가 주문은 체결가를 갖고 걸려 있는 주문은 갖지 않는다.
     *
     * <p>이 규칙이 타입에 있어야 장부 브로커와 거래소 어댑터가 같은 약속을 지킨다 —
     * 한쪽만 체결가를 채우면 두 기록을 나란히 놓을 수 없다.
     *
     * <p><b>묻는 것이 "트리거가 있는가" 가 아니라 "걸려 있는가" 다.</b> 추격 손절은 트리거
     * 가격 없이 걸려 있으므로, 앞의 물음으로 두면 그것이 시장가로 분류돼 <b>체결가를
     * 요구받는다</b> — 그리고 걸려만 있는 주문에는 체결가가 없다.
     */
    private static void assertFillMatchesKind(OrderIntent intent, Optional<Price> fillPrice) {
        if (intent.kind().rests() == fillPrice.isPresent()) {
            throw new InvalidOrderException(
                    "%s 주문의 체결가가 맞지 않는다 — 시장가는 체결되고 걸린 주문은 기다린다"
                            .formatted(intent.kind()));
        }
    }
}
