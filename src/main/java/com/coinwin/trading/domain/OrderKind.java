package com.coinwin.trading.domain;

/**
 * 봇이 낼 수 있는 주문의 전부.
 *
 * <p><b>다섯뿐이고 늘어나지 않는다.</b> 지정가 진입을 두지 않는 이유는 장부 브로커가 그것을
 * 정직하게 체결할 수 없기 때문이다 — 봉 안에서 지정가가 닿았는지는 OHLC 로 알 수 없고,
 * 추측해서 체결하면 모의 기록이 실제보다 좋게 나온다. 이 도구에서 허용되지 않는 방향의
 * 오차다({@code CostModel} 의 슬리피지 부호와 같은 판단).
 *
 * <p><b>{@code TRAILING_STOP} 이 "걸려 있음" 과 "트리거 가격이 있음" 을 갈랐다.</b> 그전까지
 * 둘은 같은 뜻이었는데 추격 손절은 걸려만 있으면서 <b>가격을 미리 말하지 않는다</b> — 어디서
 * 터질지는 앞으로 가격이 어디까지 가는지에 달렸다. 한 물음으로 두면 추격 손절이 "시장가라서
 * 즉시 체결됐다" 로 읽히고, 체결가 없는 체결이 장부에 들어간다.
 */
public enum OrderKind {

    /** 시장가로 포지션을 연다. */
    ENTRY(false, false, false),

    /** 불리한 쪽 트리거. 닿으면 시장가로 닫는다. */
    STOP_LOSS(true, true, true),

    /** 유리한 쪽 트리거. 닿으면 시장가로 닫는다. */
    TAKE_PROFIT(true, true, true),

    /**
     * 최고점에서 정해진 폭만큼 되돌아오면 닫는다. <b>트리거 가격이 없다</b> —
     * 그 값은 가격이 어디까지 갔는가의 함수라서 걸 때는 말할 수 없다.
     */
    TRAILING_STOP(true, false, true),

    /** 지금 당장 시장가로 닫는다. */
    EXIT(true, false, false);

    private final boolean reducesPosition;
    private final boolean needsTrigger;
    private final boolean rests;

    OrderKind(boolean reducesPosition, boolean needsTrigger, boolean rests) {
        this.reducesPosition = reducesPosition;
        this.needsTrigger = needsTrigger;
        this.rests = rests;
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

    /** 트리거 가격을 <b>걸 때 말해야</b> 하는가. 추격 손절은 아니다. */
    public boolean needsTrigger() {
        return needsTrigger;
    }

    /**
     * 걸려만 있고 지금 체결되지 않는가.
     *
     * <p>체결가가 있는지 없는지가 여기서 갈린다 — 걸려 있는 주문에 체결가를 채우면 그 수가
     * 손익 계산에 그대로 들어간다.
     */
    public boolean rests() {
        return rests;
    }

    /** 추격 폭을 함께 말해야 하는가. */
    public boolean needsCallbackRate() {
        return this == TRAILING_STOP;
    }
}
