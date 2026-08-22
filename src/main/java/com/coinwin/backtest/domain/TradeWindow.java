package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.journal.domain.TradeTally;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.projection.domain.EquityCurve;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 한 구간에 <b>진입한</b> 거래만 모은 성적.
 *
 * <p>구간을 갈라 성적을 보려 할 때 캔들을 잘라 구간마다 다시 돌리고 싶어진다. <b>그러면 안
 * 된다.</b> 지표 워밍업이 구간마다 되살아나 각 구간의 앞부분에서 거래가 통째로 사라진다 —
 * 4시간봉이면 일목 워밍업만 77봉, 열이틀이 넘는다. 룩어헤드의 반대편이라 "정보를 덜 주니
 * 안전하다" 고 읽히지만, 표본을 얇게 만드는 쪽으로 결과를 왜곡한다.
 *
 * <p>그래서 전체 시계열에서 <b>한 번만</b> 돌리고 결과를 시각으로 가른다. 엔진이 인과적이므로
 * (명세 § 8 룩어헤드 방지) 뒤 구간의 거래는 앞 구간까지의 정보만으로 결정돼 있다. 그 성질이
 * 없으면 이 가르기는 성립하지 않는다.
 *
 * <p>거래를 <b>진입 시각</b>으로 가른다. 청산 시각으로 가르면 구간 경계를 걸친 거래가 아직
 * 시작하지도 않은 구간의 성적에 들어간다.
 *
 * <p><b>가르는 것은 고정 잔고를 전제한다.</b> 거래마다 시작 자본으로 크기를 정하므로 거래가
 * 서로 독립이고, 그래서 구간별로 떼어 재는 것이 정확하다. 복리에서는 앞 구간의 성적이 뒤 구간
 * 거래의 크기를 이미 바꿔 놓았으므로, 뒤 구간만 떼어 "이 구간의 성적" 이라고 부를 수 없다.
 *
 * <p>가르지 않은 전체 창은 두 모드 모두에서 정확하다. 자산 곡선의 누적식이 엔진과 같기
 * 때문이다 — 모드가 가르는 것은 사이징이지 손익의 합이 아니다.
 *
 * @param trades 시간순 거래. 이 순서가 그대로 자산 곡선의 순서다
 * @param startingEquity 이 구간을 시작할 때의 자본
 */
public record TradeWindow(List<ClosedTrade> trades, Money startingEquity) {

    public TradeWindow {
        DomainValues.required(trades, "거래 목록");
        DomainValues.required(startingEquity, "시작 자본");
        trades = List.copyOf(trades);
    }

    /** 구간에 진입한 거래만 골라 담는다. 반열림 {@code [from, to)} 는 {@link TimeRange} 정의다. */
    public static TradeWindow enteredWithin(
            TimeRange range, List<ClosedTrade> all, Money startingEquity) {
        DomainValues.required(range, "구간");
        DomainValues.required(all, "거래 목록");
        return new TradeWindow(
                all.stream().filter(trade -> range.contains(trade.openedAt())).toList(),
                startingEquity);
    }

    public int size() {
        return trades.size();
    }

    public boolean isEmpty() {
        return trades.isEmpty();
    }

    /** 승률과 실현 손익. 실제 매매 기록과 같은 집계를 쓴다 ({@code docs/adr/018}). */
    public TradeTally tally() {
        return TradeTally.over(trades);
    }

    /**
     * 총이익 / 총손실.
     *
     * <p>진 거래가 하나도 없으면 <b>비어 있다.</b> 무한대나 특정 큰 수를 돌려주면 표시하는 쪽이
     * 그것을 실제 성적으로 읽는다. 손실이 없는 표본은 손익비를 말할 수 없는 표본이다.
     */
    public Optional<BigDecimal> profitFactor() {
        BigDecimal profit = sumOf(true);
        BigDecimal loss = sumOf(false).abs();
        return loss.signum() == 0
                ? Optional.empty()
                : Optional.of(profit.divide(loss, MathContext.DECIMAL64));
    }

    /** 시작 자본에서 출발해 주어진 순서대로 손익을 더한 곡선. 첫 점은 거래 이전 상태다. */
    public EquityCurve equity() {
        List<Money> points = new ArrayList<>();
        points.add(startingEquity);
        Money running = startingEquity;
        for (ClosedTrade trade : trades) {
            running = running.plus(trade.realizedPnl());
            points.add(running);
        }
        return new EquityCurve(points);
    }

    public Percentage maxDrawdown() {
        return equity().maxDrawdown();
    }

    private BigDecimal sumOf(boolean winners) {
        return trades.stream()
                .map(trade -> trade.realizedPnl().value())
                .filter(pnl -> winners ? pnl.signum() > 0 : pnl.signum() < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
