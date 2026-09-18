package com.coinwin.trading.adapter.out.binance;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.trading.domain.OrderIntent;

/**
 * 주문 하나를 바이낸스의 말로 옮긴 질의 문자열.
 *
 * <p><b>어댑터에서 떼어 낸 이유는 이 변환이 틀리면 돈이 잘못 움직이기 때문이다.</b> 문자열을
 * 만드는 일만 하는 클래스는 네트워크 없이 검사할 수 있고, 그래서 <b>실계좌에 붙이기 전에
 * 증명된다.</b>
 *
 * <p><b>전량 청산은 {@code closePosition=true} 다.</b> 수량이 어긋나도 남는 포지션이 없다 —
 * 물타기나 부분 체결이 섞여도 전량이 닫힌다. 그래서 {@code quantity} 와 함께 쓸 수 없다.
 *
 * <p><b>{@code workingType=MARK_PRICE} 다.</b> 청산이 마크 가격으로 일어나므로 손절도 같은
 * 자를 써야 한다 — 체결가 기준 손절은 꼬리에 스치고, 마크 기준 손절은 청산과 같은 눈금이다.
 * 근거는 {@code docs/spec/exit-automation.md} 의 R1. 추격 손절도 같은 자를 쓴다.
 *
 * <p><b>추격 손절에 {@code activationPrice} 를 안 보낸다.</b> 안 보내면 지금 값에서 바로
 * 따라오기 시작하는데, R5 는 <b>1차 목표에 닿은 뒤</b>에만 이 주문을 내므로 "지금부터" 가
 * 정확히 원하는 것이다. 값을 보내면 그 자리가 하나 더 생기고 <b>그 값이 이미 지나간 값이면
 * 거래소가 거절한다</b>({@code -2021}).
 */
record BinanceOrderRequest(OrderIntent intent) {

    BinanceOrderRequest {
        DomainValues.required(intent, "주문 의도");
    }

    static BinanceOrderRequest of(OrderIntent intent) {
        return new BinanceOrderRequest(intent);
    }

    String query() {
        StringBuilder query = new StringBuilder("symbol=%s&side=%s&type=%s".formatted(
                intent.symbol().value(),
                BinanceOrderAdapter.sideOf(intent),
                BinanceOrderAdapter.typeOf(intent.kind())));
        intent.trigger().ifPresent(trigger -> query
                .append("&stopPrice=").append(trigger.value().toPlainString())
                .append("&workingType=MARK_PRICE"));
        intent.callbackRate().ifPresent(rate -> query
                .append("&callbackRate=").append(rate.asPercent().toPlainString())
                .append("&workingType=MARK_PRICE"));
        appendSize(query);
        return query.toString();
    }

    /**
     * 수량 또는 전량 표시. <b>둘을 함께 보내면 거래소가 거절한다</b> — 그리고 그 거절은
     * 손절이 안 걸린 채로 포지션이 열려 있는 상태를 만든다.
     */
    private void appendSize(StringBuilder query) {
        if (intent.closesEntirePosition()) {
            query.append("&closePosition=true");
            return;
        }
        query.append("&quantity=")
                .append(intent.quantity().orElseThrow().value().toPlainString());
        if (intent.kind().reducesPosition()) {
            query.append("&reduceOnly=true");
        }
    }
}
