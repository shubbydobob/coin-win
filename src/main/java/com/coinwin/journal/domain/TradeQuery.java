package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.Optional;

/**
 * 끝난 거래를 걸러 내는 조건. <b>비어 있는 조건은 전체 조회다.</b>
 *
 * <p>{@link #matches} 를 여기에 둔 것이 이 클래스의 핵심이다. 조건의 <b>의미</b>는 도메인이
 * 정하고 어댑터는 그것을 각자의 방식으로 수행할 뿐이다. 인메모리 어댑터는 이 술어를 그대로
 * 쓰고 JPA 어댑터는 같은 뜻의 SQL 을 만드는데, <b>둘이 같은 답을 내는지는 계약 테스트가</b>
 * 확인한다. 술어가 어댑터마다 따로 있으면 그 확인이 성립하지 않는다.
 *
 * <p>시각 구간은 {@code market.domain.TimeRange} 와 같은 <b>반열림 {@code [from, to)}</b> 다.
 * 그 타입을 그대로 쓰지 않은 이유는 {@code journal → market} 의존을 만들지 않기 위해서다 —
 * 캔들의 구간과 거래의 구간은 어휘가 겹칠 뿐 같은 개념이 아니다.
 */
public record TradeQuery(
        Optional<Instant> closedFrom,
        Optional<Instant> closedTo,
        Optional<Direction> direction,
        Optional<ExitReason> exitReason,
        Optional<Boolean> followedPlan) {

    public TradeQuery {
        DomainValues.required(closedFrom, "청산 시작 시각");
        DomainValues.required(closedTo, "청산 종료 시각");
        DomainValues.required(direction, "방향");
        DomainValues.required(exitReason, "청산 이유");
        DomainValues.required(followedPlan, "계획 준수 여부");
        assertRangeIsOrdered(closedFrom, closedTo);
    }

    /** 조건 없음. 모든 청산된 거래가 걸린다. */
    public static TradeQuery all() {
        return new TradeQuery(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public TradeQuery closedBetween(Instant from, Instant to) {
        return new TradeQuery(Optional.ofNullable(from), Optional.ofNullable(to),
                direction, exitReason, followedPlan);
    }

    public TradeQuery withDirection(Direction value) {
        return new TradeQuery(closedFrom, closedTo,
                Optional.ofNullable(value), exitReason, followedPlan);
    }

    public TradeQuery withExitReason(ExitReason value) {
        return new TradeQuery(closedFrom, closedTo,
                direction, Optional.ofNullable(value), followedPlan);
    }

    public TradeQuery withFollowedPlan(Boolean value) {
        return new TradeQuery(closedFrom, closedTo,
                direction, exitReason, Optional.ofNullable(value));
    }

    /** 이 거래가 조건에 드는가. 조건이 비어 있는 항목은 통과시킨다. */
    public boolean matches(ClosedTrade trade) {
        DomainValues.required(trade, "거래");
        return closedFrom.map(from -> !trade.closedAt().isBefore(from)).orElse(true)
                && closedTo.map(to -> trade.closedAt().isBefore(to)).orElse(true)
                && direction.map(value -> trade.plan().direction() == value).orElse(true)
                && exitReason.map(value -> trade.closure().reason() == value).orElse(true)
                && followedPlan.map(value -> trade.followedPlan() == value).orElse(true);
    }

    private static void assertRangeIsOrdered(Optional<Instant> from, Optional<Instant> to) {
        if (from.isPresent() && to.isPresent() && to.get().isBefore(from.get())) {
            throw new InvalidTradeException(
                    "조회 구간의 끝은 시작보다 앞설 수 없다: %s ~ %s".formatted(from.get(), to.get()));
        }
    }
}
