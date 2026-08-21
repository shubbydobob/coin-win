package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 끝난 거래들의 집계. <b>계획 준수 여부로 먼저 가르고, 그다음에 손익을 센다.</b>
 *
 * <p>순서가 반대면 안 된다. 전체 손익을 먼저 내고 나중에 준수 여부를 덧붙이면, 규칙을 어기고
 * 크게 이긴 거래 하나가 전체를 흑자로 만들면서 "지금 방식이 통한다" 는 결론을 만든다.
 * {@code scope.md} 가 든 두 번째 문제 — 사후 분석이 불가능하다 — 가 정확히 그 지점이다.
 *
 * <p>{@link ClosedTrade} 목록만 받는다. 미청산 거래를 걸러 내는 일을 부르는 쪽에 맡기지 않는
 * 것이 {@link Trade} 를 세 타입으로 나눈 이유다 — 필터를 빠뜨리면 컴파일이 되지 않는다.
 */
public record JournalSummary(
        TradeTally followed,
        TradeTally broken,
        Money lossIfEveryStopHonored,
        TradeIntervals intervals) {

    public JournalSummary {
        DomainValues.required(followed, "계획 준수 집계");
        DomainValues.required(broken, "계획 위반 집계");
        DomainValues.required(lossIfEveryStopHonored, "반사실 손실");
        DomainValues.required(intervals, "거래 간격");
    }

    public static JournalSummary empty() {
        return new JournalSummary(
                TradeTally.empty(), TradeTally.empty(), Money.of("0"), TradeIntervals.none());
    }

    /**
     * 거래 목록을 집계한다. 목록은 <b>진입 시각 순으로 정렬해서</b> 본다.
     *
     * <p>정렬이 필요한 것은 간격 때문이다. {@code ExecutedEntries} 가 순서를 고쳐 주지 않고
     * 거부하는 것과 다른 판단인데, 그쪽은 순서 자체가 기록의 일부(분할 진입의 체결 순서)인
     * 반면 여기서 넘어오는 목록의 순서는 조회 결과의 부산물이기 때문이다.
     */
    public static JournalSummary of(List<ClosedTrade> trades) {
        DomainValues.required(trades, "거래 목록");
        if (trades.isEmpty()) {
            return empty();
        }
        List<ClosedTrade> chronological = trades.stream()
                .sorted(Comparator.comparing(ClosedTrade::openedAt))
                .toList();
        Map<Boolean, List<ClosedTrade>> byAdherence = chronological.stream()
                .collect(Collectors.partitioningBy(ClosedTrade::followedPlan));
        List<ClosedTrade> broken = byAdherence.get(false);
        return new JournalSummary(
                TradeTally.over(byAdherence.get(true)),
                TradeTally.over(broken),
                counterfactualLoss(broken),
                TradeIntervals.over(chronological));
    }

    public int totalTrades() {
        return followed.trades() + broken.trades();
    }

    public Money totalRealizedPnl() {
        return followed.realizedPnl().plus(broken.realizedPnl());
    }

    /** 계획대로 닫은 거래의 비율. 이 도구가 실제로 쓰이고 있는지를 보는 수치다. */
    public Percentage planAdherence() {
        return totalTrades() == 0
                ? Percentage.of("0")
                : Percentage.ofRatio(followed.trades(), totalTrades());
    }

    /**
     * 계획을 어겨서 얻은 것의 합. <b>음수면 어기는 편이 손해였다는 뜻이다.</b>
     *
     * <p>{@code 어긴 거래들의 실제 손익 - 그 거래들을 손절가에서 닫았을 때의 손익}. 이 한 수치가
     * "규칙을 지켰어야 했는가" 에 대한 답이고, 손익만 쌓아서는 결코 나오지 않는다.
     */
    public Money costOfDeviation() {
        return broken.realizedPnl().minus(lossIfEveryStopHonored);
    }

    private static Money counterfactualLoss(List<ClosedTrade> trades) {
        return trades.stream()
                .map(ClosedTrade::lossIfStopHonored)
                .reduce(Money.of("0"), Money::plus);
    }
}
