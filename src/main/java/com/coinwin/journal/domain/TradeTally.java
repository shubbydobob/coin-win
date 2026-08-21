package com.coinwin.journal.domain;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.List;

/**
 * 거래 한 무리의 성적. {@link JournalSummary} 가 계획 준수 쪽과 위반 쪽에 하나씩 들고 있다.
 *
 * <p>같은 계산을 두 벌 쓰지 않으려고 뽑아냈다. 두 무리의 승률을 각각 다른 코드로 내면
 * "무엇을 승리로 세는가" 가 갈릴 수 있는데, 그러면 두 수치를 나란히 놓는 것 자체가 무의미해진다.
 */
public record TradeTally(int trades, Money realizedPnl, int wins) {

    /** 거래가 하나도 없는 무리. 손익도 승수도 0 이다. */
    public static TradeTally empty() {
        return new TradeTally(0, Money.of("0"), 0);
    }

    /**
     * 이 무리의 성적.
     *
     * <p>공개해 둔 이유는 Phase 6 백테스트가 같은 집계를 필요로 하기 때문이다. 거기서 승수를
     * 따로 세면 "무엇을 승리로 세는가"(0 원은 승리가 아니다)가 두 곳에 생기고, 실제 매매와
     * 백테스트의 승률을 나란히 놓는 것 자체가 무의미해진다. 이 record 가 존재하는 이유와 같다.
     */
    public static TradeTally over(List<ClosedTrade> trades) {
        Money pnl = trades.stream()
                .map(ClosedTrade::realizedPnl)
                .reduce(Money.of("0"), Money::plus);
        int wins = (int) trades.stream()
                .filter(trade -> trade.realizedPnl().value().signum() > 0)
                .count();
        return new TradeTally(trades.size(), pnl, wins);
    }

    public boolean isEmpty() {
        return trades == 0;
    }

    /**
     * 승률. <b>0 원으로 끝난 거래는 승리가 아니다</b> — 수수료를 내고 본전이면 이긴 것이 아니다.
     *
     * <p>거래가 없으면 0% 다. 승률을 낼 수 없는 것과 0% 인 것은 다르지만, 여기서 {@code Optional}
     * 을 내면 표시하는 쪽마다 빈 무리를 처리하는 분기가 생긴다. 건수가 함께 있으므로
     * 0 건 / 0% 는 구분된다.
     */
    public Percentage winRate() {
        return isEmpty() ? Percentage.of("0") : Percentage.ofRatio(wins, trades);
    }

    public int losses() {
        return trades - wins;
    }
}
