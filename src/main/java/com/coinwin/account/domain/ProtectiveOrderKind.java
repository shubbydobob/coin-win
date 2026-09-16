package com.coinwin.account.domain;

/**
 * 포지션을 닫는 미체결 주문의 종류.
 *
 * <p><b>손절과 익절을 한 값으로 뭉치지 않는다.</b> {@code docs/spec/exit-automation.md} § 0 의
 * 두 실패 모드가 정확히 이 둘로 갈리기 때문이다 — 익절을 걸어 두고 손절을 안 거는 것은
 * "아무것도 안 건 것" 과 다른 사실이고, 화면이 그 둘에 다른 말을 해야 한다.
 */
public enum ProtectiveOrderKind {

    /** 불리한 쪽에서 트리거되는 주문. {@code STOP_MARKET} · {@code STOP}. */
    STOP_LOSS(true),

    /** 유리한 쪽에서 트리거되는 주문. {@code TAKE_PROFIT_MARKET} · {@code TAKE_PROFIT}. */
    TAKE_PROFIT(false),

    /**
     * 고점(저점)을 따라 올라가는 손절. {@code TRAILING_STOP_MARKET}.
     *
     * <p><b>이것도 손절로 센다.</b> 트리거 가격이 미리 정해져 있지 않을 뿐 하는 일은 같다 —
     * 왼쪽 꼬리를 자른다. 세지 않으면 추격 손절만 걸어 둔 사람에게 "손절이 없다" 는 거짓
     * 경고가 뜨고, <b>늘 떠 있는 경고는 아무것도 경고하지 않는다.</b>
     */
    TRAILING_STOP(true);

    private final boolean stopsLoss;

    ProtectiveOrderKind(boolean stopsLoss) {
        this.stopsLoss = stopsLoss;
    }

    /** 손실을 자르는 종류인가. 익절만 이것이 거짓이다. */
    public boolean stopsLoss() {
        return stopsLoss;
    }
}
