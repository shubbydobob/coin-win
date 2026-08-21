package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;

/**
 * 거래 빈도 — 주당 몇 건을 몇 주 동안.
 *
 * <p>총 거래 수 하나로 받지 않는 이유는 매매 계획이 "주 2회, 1년" 같은 형태로 서기 때문이다.
 * 같은 100 거래라도 그것이 1년치인지 한 달치인지에 따라 사람이 내리는 판단이 다르다.
 */
public record TradeFrequency(int tradesPerWeek, int weeks) {

    /** 시뮬레이션 비용은 {@code 거래 수 × 시행 횟수} 로 늘어난다. 상한이 없으면 요청 하나가 서버를 잡는다. */
    private static final int MAXIMUM_TRADES = 10_000;

    public TradeFrequency {
        DomainValues.atLeast(tradesPerWeek, 1, "주당 거래 수");
        DomainValues.atLeast(weeks, 1, "기간(주)");
        if (Math.multiplyFull(tradesPerWeek, weeks) > MAXIMUM_TRADES) {
            throw new InvalidProjectionException(
                    "총 거래 수는 " + MAXIMUM_TRADES + " 을 넘을 수 없다: "
                            + tradesPerWeek + " × " + weeks);
        }
    }

    public int totalTrades() {
        return tradesPerWeek * weeks;
    }
}
