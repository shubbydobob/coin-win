package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;

/**
 * 한 포지션에서 <b>나가는 가격들이 어디인가.</b> 손절 · 1차 목표 · 본전.
 *
 * <p>{@link ExitRuleStrategy} 에서 떼어 낸 이유는 길이 때문만이 아니다. 이 넷은 전부
 * <b>"유리한 쪽" 과 "불리한 쪽" 의 부호 문제</b>이고 숏에서 한 번만 뒤집으면 손절이 이익
 * 구간에서 터진다. 한자리에 모으면 그 부호가 두 메서드({@code toward} · {@code away})에만
 * 있고, 규칙 쪽은 <b>언제 무엇을 거는가</b>만 말한다.
 *
 * @param stopDistance 진입가에서 손절까지(%)
 * @param firstTarget 진입가에서 1차 익절까지(%)
 * @param roundTripCost 왕복 비용(%)
 */
record ExitPrices(Percentage stopDistance, Percentage firstTarget, Percentage roundTripCost) {

    ExitPrices {
        DomainValues.required(stopDistance, "손절 거리");
        DomainValues.required(firstTarget, "1차 익절 거리");
        DomainValues.required(roundTripCost, "왕복 비용");
    }

    /**
     * 처음 거는 손절. <b>진입가 기준이다</b> — 표시가 기준으로 두면 사이클마다 값이 달라져
     * 의도치 않은 추격 손절이 된다.
     *
     * <p>다만 이미 그만큼 밀린 포지션이면 그 값이 표시가 너머라 <b>거는 즉시 체결된다</b> —
     * 그것은 손절이 아니라 시장가 청산이고 사람이 원한 적 없는 매매다. 그때만 표시가에서 잰다.
     */
    Price initialStop(Price mark, BotPosition open) {
        Price fromEntry = away(open.entry(), open.direction(), stopDistance);
        boolean alreadyPast = open.direction() == Direction.LONG
                ? !mark.isAbove(fromEntry)
                : !mark.isBelow(fromEntry);
        return alreadyPast ? away(mark, open.direction(), stopDistance) : fromEntry;
    }

    /**
     * 본전. <b>진입가가 아니라 왕복 비용만큼 유리한 쪽이다</b> — 진입가에 걸면 수수료만큼
     * 지고 끝난다. 명세가 "진입가(+수수료)" 라고 적은 자리다.
     */
    Price breakEven(BotPosition open) {
        return toward(open.entry(), open.direction(), roundTripCost);
    }

    /** 1차 익절이 놓이는 자리. */
    Price target(BotPosition open) {
        return toward(open.entry(), open.direction(), firstTarget);
    }

    /** 표시가가 1차 목표에 닿았거나 지났는가. */
    boolean reached(Price mark, BotPosition open) {
        Price target = target(open);
        return open.direction() == Direction.LONG ? !mark.isBelow(target) : !mark.isAbove(target);
    }

    /** 불리한 쪽 — 롱이면 아래, 숏이면 위. */
    private static Price away(Price from, Direction direction, Percentage distance) {
        Money offset = distance.applyTo(from.asAmount());
        return direction == Direction.LONG ? from.minus(offset) : from.plus(offset);
    }

    /** 유리한 쪽 — 롱이면 위, 숏이면 아래. */
    private static Price toward(Price from, Direction direction, Percentage distance) {
        Money offset = distance.applyTo(from.asAmount());
        return direction == Direction.LONG ? from.plus(offset) : from.minus(offset);
    }
}
