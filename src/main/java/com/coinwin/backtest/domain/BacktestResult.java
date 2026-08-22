package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.JournalSummary;
import com.coinwin.journal.domain.TradeTally;
import com.coinwin.projection.domain.EquityCurve;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 백테스트 한 번의 결과.
 *
 * <p>거래를 {@link ClosedTrade} 로 담는다 — 실제 매매 기록과 <b>같은 타입</b>이다. 그래서
 * {@link JournalSummary} 를 그대로 씌울 수 있고, 백테스트로 검증한 전략과 실제로 한 매매를
 * 나란히 놓고 볼 수 있다. 근거는 {@code docs/adr/018}.
 *
 * <p>승률을 여기서 세지 않고 {@link TradeTally} 에 맡기는 이유도 같다. "0 원으로 끝난 거래는
 * 승리가 아니다" 같은 규칙이 두 곳에 생기면 두 수치를 비교하는 것 자체가 무의미해진다.
 *
 * @param spec 이 결과를 만든 입력 전부. 같은 스펙은 같은 결과를 낸다
 * @param trades 시간순 거래
 * @param equity 자산 곡선. 첫 점은 거래 이전의 초기 자본이다
 */
public record BacktestResult(BacktestSpec spec, List<ClosedTrade> trades, EquityCurve equity) {

    public BacktestResult {
        DomainValues.required(spec, "백테스트 스펙");
        DomainValues.required(trades, "거래 목록");
        DomainValues.required(equity, "자산 곡선");
        trades = List.copyOf(trades);
    }

    public TradeTally tally() {
        return TradeTally.over(trades);
    }

    /** 계획 준수별 분리·반사실 손실·거래 간격. Phase 5 의 집계를 그대로 쓴다. */
    public JournalSummary journal() {
        return JournalSummary.of(trades);
    }

    public int totalTrades() {
        return trades.size();
    }

    public Percentage winRate() {
        return tally().winRate();
    }

    public Money netPnl() {
        return tally().realizedPnl();
    }

    public Percentage maxDrawdown() {
        return equity.maxDrawdown();
    }

    public Money finalEquity() {
        return equity.finalEquity();
    }

    /**
     * 총이익 / 총손실. 진 거래가 없으면 비어 있다 — 규칙과 구현 모두 {@link TradeWindow} 에 있다.
     *
     * <p>여기서 다시 세지 않는 이유는 승률을 {@link TradeTally} 에 맡기는 이유와 같다. 구간별
     * 성적표가 같은 수치를 다른 코드로 내면 전체와 구간을 나란히 놓는 것 자체가 무의미해진다.
     */
    public Optional<BigDecimal> profitFactor() {
        return window().profitFactor();
    }

    /** 전체 구간을 하나의 창으로 본 것. 구간별 성적은 같은 타입을 잘라서 낸다. */
    public TradeWindow window() {
        return new TradeWindow(trades, spec.account().initialCapital());
    }
}
