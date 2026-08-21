package com.coinwin.ai.domain;

import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.journal.domain.ClosedTrade;
import java.util.Optional;

/**
 * 거래를 사람이 읽는 문장으로 옮긴다. <b>렌더링도 도메인 규칙이다</b> — 어댑터마다 다른 문장을
 * 만들면 같은 거래가 어떤 경로로 색인됐는지에 따라 다르게 검색된다.
 *
 * <p>수치를 그대로 적는 이유는 의미 검색이 문장을 본다는 것 때문이다. 메타데이터에만 두면
 * "손실 직후" 같은 말로 물었을 때 걸리지 않는다. 같은 사실을 양쪽에 둔다.
 */
final class TradeSentences {

    private TradeSentences() {
    }

    static String describe(ClosedTrade trade, Optional<ClosedTrade> previous) {
        return String.join(" ",
                outcome(trade),
                position(trade),
                context(trade),
                sequence(trade, previous));
    }

    private static String outcome(ClosedTrade trade) {
        return "%s %d배 거래. 평단 %s 에 %s 개를 잡아 %s 에 닫았다(%s). 실현 손익 %s USDT. %s."
                .formatted(
                        korean(trade),
                        trade.plan().leverage(),
                        trade.averageEntryPrice().value().toPlainString(),
                        trade.entries().totalQuantity().value().toPlainString(),
                        trade.closure().exit().price().value().toPlainString(),
                        trade.closure().reason().name(),
                        trade.realizedPnl().value().toPlainString(),
                        trade.followedPlan() ? "계획을 지켰다" : "계획을 어겼다");
    }

    private static String position(ClosedTrade trade) {
        return "손절가는 %s, 익절가는 %s 였고 %d 분을 들고 있었다.".formatted(
                trade.plan().stopLoss().value().toPlainString(),
                trade.plan().takeProfit().value().toPlainString(),
                trade.holdingPeriod().toMinutes());
    }

    private static String context(ClosedTrade trade) {
        return "진입 시점 가격은 일목 구름 %s, 볼린저 밴드 %s 였다. 진입 근거: %s".formatted(
                korean(trade.context().ichimokuPosition()),
                korean(trade.context().bollingerPosition()),
                trade.context().rationale());
    }

    /**
     * 직전 거래에 대한 사실. <b>첫 거래에는 없다</b> — 없는 것을 "손실 직후가 아니었다" 로
     * 적으면 거짓말이 된다.
     */
    private static String sequence(ClosedTrade trade, Optional<ClosedTrade> previous) {
        return previous.filter(trade::opensAfter)
                .map(before -> "%s 이 거래는 %d 분 만에 다시 들어간 것이다.".formatted(
                        before.realizedPnl().value().signum() < 0
                                ? "직전 거래는 손실이었다."
                                : "직전 거래는 이익이었다.",
                        trade.timeSincePreviousTrade(before).toMinutes()))
                .orElse("이 기록에서 첫 거래다.");
    }

    private static String korean(ClosedTrade trade) {
        return switch (trade.plan().direction()) {
            case LONG -> "롱";
            case SHORT -> "숏";
        };
    }

    private static String korean(BandPosition position) {
        return switch (position) {
            case ABOVE -> "위";
            case INSIDE -> "안";
            case BELOW -> "아래";
        };
    }
}
