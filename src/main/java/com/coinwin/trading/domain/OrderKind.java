package com.coinwin.trading.domain;

/**
 * 봇이 낼 수 있는 주문의 전부.
 *
 * <p><b>넷뿐이고 늘어나지 않는다.</b> 지정가 진입을 두지 않는 이유는 장부 브로커가 그것을
 * 정직하게 체결할 수 없기 때문이다 — 봉 안에서 지정가가 닿았는지는 OHLC 로 알 수 없고,
 * 추측해서 체결하면 모의 기록이 실제보다 좋게 나온다. 이 도구에서 허용되지 않는 방향의
 * 오차다({@code CostModel} 의 슬리피지 부호와 같은 판단).
 */
public enum OrderKind {

    /** 시장가로 포지션을 연다. */
    ENTRY(false),

    /** 불리한 쪽 트리거. 닿으면 시장가로 닫는다. */
    STOP_LOSS(true),

    /** 유리한 쪽 트리거. 닿으면 시장가로 닫는다. */
    TAKE_PROFIT(true),

    /** 지금 당장 시장가로 닫는다. */
    EXIT(true);

    private final boolean reducesPosition;

    OrderKind(boolean reducesPosition) {
        this.reducesPosition = reducesPosition;
    }

    /**
     * 포지션을 줄이기만 하는 주문인가.
     *
     * <p>거래소의 {@code reduceOnly} 로 나간다. <b>이것이 참인 주문은 포지션을 뒤집을 수
     * 없다</b> — 수량 계산이 틀려도 반대 포지션이 열리지 않는다는 뜻이고, 자동으로 도는
     * 루프에서 그 보장은 값이 크다.
     */
    public boolean reducesPosition() {
        return reducesPosition;
    }

    /** 트리거 가격이 필요한가. */
    public boolean needsTrigger() {
        return this == STOP_LOSS || this == TAKE_PROFIT;
    }
}
