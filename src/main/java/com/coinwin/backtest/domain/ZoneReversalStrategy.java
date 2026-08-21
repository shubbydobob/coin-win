package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.domain.MarketContext;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.InvalidPositionPlanException;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import java.util.Optional;

/**
 * 지지·저항 대의 근단에서 반전 방향으로 진입하는 전략. 돌파 매매는 하지 않는다.
 *
 * <p><b>이 타입은 계좌를 모른다.</b> 잔고·수량·증거금이 걸린 판정(리스크 초과, 손절이 청산가
 * 너머, 증거금 초과)은 예산을 가진 엔진이 한다. 시장 논리와 계좌 논리를 나눠 두면 전략을
 * 같은 캔들에 여러 잔고로 돌려도 신호가 바뀌지 않는다.
 *
 * <p>버리는 신호는 예외가 아니라 {@link Optional#empty()} 다. 예외를 흘리면 백테스트가 중간에
 * 멈추고, 그러면 "거를 신호가 있었다" 는 사실이 결과에서 사라진다.
 */
public record ZoneReversalStrategy(EntryRules rules) {

    private static final Percentage HALF = Percentage.of("50");

    public ZoneReversalStrategy {
        DomainValues.required(rules, "진입 규칙");
    }

    /**
     * 이 봉의 종가로 판단한 진입 계획. 주문은 <b>다음 봉부터</b> 유효하다.
     *
     * @param leverage 계좌 설정이지만 계획의 일부라 여기서 받는다
     */
    public Optional<TradeSignal> signalAt(
            MarketSnapshot snapshot, IndicatorReading reading, int leverage) {
        DomainValues.required(snapshot, "시장 스냅샷");
        DomainValues.required(reading, "지표 판정");
        if (snapshot.zones().containsPrice(snapshot.close())) {
            return Optional.empty();
        }
        return nearestCandidate(snapshot)
                .filter(candidate -> passesFilter(candidate, reading))
                .flatMap(candidate -> toSignal(snapshot, reading, candidate, leverage));
    }

    /**
     * 근단이 종가에 더 가까운 쪽 하나만 무장한다.
     *
     * <p>지지와 저항이 <b>둘 다 있어야</b> 후보가 된다. 익절가가 반대편 대의 근단이므로 한쪽만
     * 있으면 계획을 세울 수 없다.
     *
     * <p>거리가 정확히 같으면 고를 근거가 없어 버린다. 둘 다 무장하고 먼저 체결되는 쪽을 쓰는
     * 선택지도 있었으나, 한 봉 안에서 양쪽 지정가가 모두 닿으면 순서를 정할 근거가 다시 없다.
     */
    private static Optional<Candidate> nearestCandidate(MarketSnapshot snapshot) {
        Price close = snapshot.close();
        Optional<PriceZone> support = snapshot.zones().nearestSupport(close);
        Optional<PriceZone> resistance = snapshot.zones().nearestResistance(close);
        if (support.isEmpty() || resistance.isEmpty()) {
            return Optional.empty();
        }
        Money toSupport = close.absoluteDifference(support.get().nearEdgeFor(Direction.LONG));
        Money toResistance = close.absoluteDifference(resistance.get().nearEdgeFor(Direction.SHORT));
        if (toSupport.equals(toResistance)) {
            return Optional.empty();
        }
        return Optional.of(toSupport.isGreaterThan(toResistance)
                ? new Candidate(resistance.get(), ZoneRole.RESISTANCE, support.get())
                : new Candidate(support.get(), ZoneRole.SUPPORT, resistance.get()));
    }

    private boolean passesFilter(Candidate candidate, IndicatorReading reading) {
        return !rules.indicatorFilter() || reading.agreesWith(candidate.direction());
    }

    private Optional<TradeSignal> toSignal(MarketSnapshot snapshot, IndicatorReading reading,
            Candidate candidate, int leverage) {
        Optional<PositionPlan> plan = planFor(snapshot, candidate, leverage)
                .filter(built -> !built.riskRewardBelow(rules.minRiskReward()));
        return plan.map(accepted -> new TradeSignal(candidate.direction(), accepted,
                new MarketContext(snapshot.close(), reading.ichimoku(), reading.bollinger(),
                        candidate.zone().describeAs(candidate.role()))));
    }

    /**
     * 근단·원단을 진입가로, 원단 너머를 손절로, 반대편 대의 근단을 익절로 세운 계획.
     *
     * <p>{@link PositionPlan} 이 거부하면 신호가 없는 것으로 흡수한다. 버퍼가 0 이면 손절가가
     * 최저(최고) 진입가와 같아져 거부되는데, 그것은 오류가 아니라 그 설정에서 성립하지 않는
     * 계획일 뿐이다.
     */
    private Optional<PositionPlan> planFor(
            MarketSnapshot snapshot, Candidate candidate, int leverage) {
        Direction direction = candidate.direction();
        Price near = candidate.zone().nearEdgeFor(direction);
        Price far = candidate.zone().farEdgeFor(direction);
        Money buffer = snapshot.atr().times(rules.stopBufferMultiple());
        try {
            return Optional.of(new PositionPlan(direction,
                    EntryLadder.of(new PlannedEntry(near, HALF), new PlannedEntry(far, HALF)),
                    stopBeyond(far, buffer, direction),
                    candidate.target().nearEdgeFor(candidate.role().opposite().entryDirection()),
                    leverage));
        } catch (InvalidPositionPlanException rejected) {
            return Optional.empty();
        }
    }

    private static Price stopBeyond(Price farEdge, Money buffer, Direction direction) {
        return switch (direction) {
            case LONG -> farEdge.minus(buffer);
            case SHORT -> farEdge.plus(buffer);
        };
    }

    /** 무장할 대와 그 역할, 그리고 익절 목표가 될 반대편 대. */
    private record Candidate(PriceZone zone, ZoneRole role, PriceZone target) {

        Direction direction() {
            return role.entryDirection();
        }
    }
}
