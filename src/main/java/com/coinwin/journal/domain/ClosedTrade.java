package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.PositionPlan;
import java.time.Duration;
import java.time.Instant;

/**
 * 끝난 거래. 이 모듈의 질문에 답할 수 있는 유일한 상태다.
 *
 * <p>세 값이 핵심이다 — {@link #realizedPnl()}, {@link #followedPlan()},
 * {@link #lossIfStopHonored()}. 앞의 둘은 <b>손익과 계획 준수를 분리해 집계하기</b> 위한 것이고,
 * 셋째는 계획을 어긴 거래에 "지켰다면 어땠을까" 를 되묻기 위한 것이다.
 *
 * <p>손익은 입력받지 않고 <b>체결 내역에서 계산한다.</b> 거래소 화면의 숫자를 그대로 받으면
 * 체결 내역과 손익이 어긋나 있어도 알 방법이 없다. 재현할 수 없는 것(수수료·펀딩비)만
 * {@link TradeCosts} 로 받는다.
 */
public record ClosedTrade(
        TradeId id,
        PositionPlan plan,
        ExecutedEntries entries,
        MarketContext context,
        Instant plannedAt,
        TradeClosure closure) implements Trade {

    public ClosedTrade {
        DomainValues.required(id, "거래 식별자");
        DomainValues.required(plan, "매매 계획");
        DomainValues.required(entries, "진입 체결 내역");
        DomainValues.required(context, "진입 시점 시장 상태");
        DomainValues.required(plannedAt, "계획 시각");
        DomainValues.required(closure, "청산 정보");
    }

    public Instant openedAt() {
        return entries.firstFilledAt();
    }

    public Instant closedAt() {
        return closure.exit().at();
    }

    /** 포지션을 들고 있던 시간. */
    public Duration holdingPeriod() {
        return Duration.between(openedAt(), closedAt());
    }

    public Price averageEntryPrice() {
        return entries.averagePrice();
    }

    /** 계획대로 닫혔는가. 손실이어도 참일 수 있고, 이익이어도 거짓일 수 있다. */
    public boolean followedPlan() {
        return closure.honorsPlan();
    }

    /** 비용을 빼기 전 손익. 청산가와 평단의 차이만 본다. */
    public Money grossPnl() {
        return pnlAt(closure.exit().price());
    }

    /** 실제로 계좌에 남은 것. {@code 총손익 - 수수료 - 펀딩비}. */
    public Money realizedPnl() {
        return grossPnl().minus(closure.costs().total());
    }

    /**
     * 반사실 — <b>손절을 지켰다면</b> 얼마를 잃었을까.
     *
     * <p>{@link #grossPnl()} 과 <b>같은 공식</b>에 청산가 대신 계획한 손절가를 넣는다. 두
     * 계산이 갈라지면 "실제 대 반사실" 비교가 성립하지 않으므로 식을 하나로 둔다.
     *
     * <p>비용은 <b>수수료만</b> 뺀다. 손절을 지켰다면 보유 기간이 짧아 펀딩비가 달랐을 텐데
     * 그 값은 알 수 없다. 실제 펀딩비를 그대로 쓰면 반사실이 실제만큼 나빠 보이고, 그러면
     * {@link #costOfDeviation()} 이 계획을 어긴 대가를 과소평가한다. <b>오래 들고 있어서 낸
     * 펀딩비는 어긴 대가의 일부</b>이므로 반사실 쪽에 넣지 않는 것이 맞다.
     */
    public Money lossIfStopHonored() {
        return pnlAt(plan.stopLoss()).minus(closure.costs().fees());
    }

    /**
     * 계획을 어겨서 얻은 것. {@code 실제 손익 - 반사실 손익} 이므로 <b>음수면 어긴 대가</b>다.
     *
     * <p>계획대로 손절된 거래에서는 0 에 가깝다. 이 값이 의미를 갖는 경우는
     * {@link ExitReason#HELD_PAST_STOP} 과 {@link ExitReason#LIQUIDATED} 다.
     */
    public Money costOfDeviation() {
        return realizedPnl().minus(lossIfStopHonored());
    }

    /**
     * 직전 거래를 닫은 뒤 이 거래에 들어가기까지 걸린 시간.
     *
     * <p>손실 직후 곧바로 다시 들어가는 것(복수 매매)이 계좌를 망가뜨리는 흔한 경로다.
     * 그것을 보려면 손익이 아니라 <b>간격</b>이 필요하다.
     *
     * @throws InvalidTradeException 직전 거래가 이 거래보다 늦게 닫힌 경우. 목록이 시간순으로
     *     정렬되지 않았다는 뜻이며, 음수 간격이 조용히 평균에 섞이는 것을 막는다.
     */
    public Duration timeSincePreviousTrade(ClosedTrade previous) {
        DomainValues.required(previous, "직전 거래");
        if (!opensAfter(previous)) {
            throw new InvalidTradeException(
                    "직전 거래는 이 거래가 열리기 전에 닫혀 있어야 한다: 직전 청산 %s, 진입 %s"
                            .formatted(previous.closedAt(), openedAt()));
        }
        return Duration.between(previous.closedAt(), openedAt());
    }

    /**
     * 직전 거래가 닫힌 뒤에 이 거래가 열렸는가.
     *
     * <p>{@link #timeSincePreviousTrade} 의 전제조건과 <b>같은 판정</b>이다. 술어를 따로 두는
     * 이유는 집계 쪽이 "물어봐도 되는지" 를 예외를 잡아서가 아니라 물어서 알 수 있게
     * 하기 위해서다. 두 곳에 부등호를 각각 쓰면 언젠가 한쪽만 경계가 바뀐다.
     */
    public boolean opensAfter(ClosedTrade previous) {
        DomainValues.required(previous, "직전 거래");
        return !previous.closedAt().isAfter(openedAt());
    }

    /**
     * 이 가격에 전량 닫았을 때의 손익. 부호는 방향이 정한다.
     *
     * <p>{@code Price.absoluteDifference} 를 쓰지 않는 이유가 여기 있다. 손익은 부호가 전부다 —
     * 절대값을 쓰면 손실과 이익이 같은 값이 된다.
     */
    private Money pnlAt(Price price) {
        Money entry = entries.averagePrice().asAmount();
        Money exit = price.asAmount();
        Money perUnit = switch (plan.direction()) {
            case LONG -> exit.minus(entry);
            case SHORT -> entry.minus(exit);
        };
        return entries.totalQuantity().times(perUnit);
    }
}
