package com.coinwin.trading.domain;

/**
 * 봇이 낸 주문이 어디로 가는가.
 *
 * <p><b>기본은 {@link #PAPER} 다.</b> 같은 루프·같은 판단·같은 주문 객체가 만들어지고 마지막
 * 한 칸에서 거래소 대신 장부에 적힌다. 실계좌는 환경변수를 명시적으로 바꿔야 한다.
 *
 * <p><b>폴백이 없다.</b> {@link #LIVE} 로 뜨지 못하면 {@code PAPER} 로 내려오지 않고 앱이 그
 * 자리에서 실패한다 — "실계좌인 줄 알았는데 장부였다" 와 "장부인 줄 알았는데 실계좌였다" 는
 * 둘 다 최악이고, 뒤쪽은 돈이 든다. {@code account} 가 키 없을 때 인메모리로 대신 올리지
 * 않기로 한 것과 같은 규칙이다.
 *
 * <p>이 값이 <b>화면과 로그와 응답의 모든 줄에 붙는다.</b> 어느 모드로 돌고 있는지 모르는
 * 상태가 존재하면 안 된다.
 */
public enum TradingMode {

    /** 장부에만 적는다. 거래소에 아무것도 안 보낸다. 돈이 들지 않는다. */
    PAPER(false),

    /** 바이낸스 테스트넷. 진짜 주문 경로를 지나지만 가짜 돈이다. */
    TESTNET(false),

    /** 실계좌. <b>여기서만 돈이 움직인다.</b> */
    LIVE(true);

    private final boolean realMoney;

    TradingMode(boolean realMoney) {
        this.realMoney = realMoney;
    }

    /** 이 모드에서 잃을 수 있는 것이 진짜 돈인가. */
    public boolean movesRealMoney() {
        return realMoney;
    }
}
