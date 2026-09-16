package com.coinwin.account.adapter.in.web;

import com.coinwin.account.domain.ProtectiveOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** 거래소에 걸려 있는 포지션 종료 주문 한 건. */
@Schema(description = "포지션을 닫는 미체결 주문 한 건")
public record ProtectiveOrderResponse(
        @Schema(description = """
                STOP_LOSS 는 불리한 쪽에서 트리거된다.
                TAKE_PROFIT 은 유리한 쪽에서 트리거된다.
                TRAILING_STOP 은 고점(저점)을 따라가는 손절이며 손절로 센다.""",
                example = "STOP_LOSS",
                allowableValues = {"STOP_LOSS", "TAKE_PROFIT", "TRAILING_STOP"})
        String kind,

        @Schema(description = """
                트리거 가격. 추격 손절은 고점을 따라 움직이므로 미리 정해진 값이 없어 null 이다.
                0 을 넣지 않는다 — 0 원짜리 트리거는 '지금 당장'으로 읽힌다.""",
                nullable = true, example = "58000.00")
        BigDecimal triggerPrice,

        @Schema(description = """
                닫을 수량. **null 이면 전량이다**(거래소의 closePosition=true).
                0 으로 적지 않는다 — 전량과 '아무것도 안 닫음'이 같은 값이 된다.""",
                nullable = true, example = "0.05000000")
        BigDecimal quantity) {

    static ProtectiveOrderResponse from(ProtectiveOrder order) {
        return new ProtectiveOrderResponse(
                order.kind().name(),
                order.triggerPrice().map(price -> price.value()).orElse(null),
                order.quantity().map(amount -> amount.value()).orElse(null));
    }
}
