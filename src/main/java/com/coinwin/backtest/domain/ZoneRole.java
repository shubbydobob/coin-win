package com.coinwin.backtest.domain;

import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.position.domain.Direction;
import java.util.Optional;

/**
 * 대가 지금 무엇으로 작동하는가. <b>대에 저장되는 값이 아니라 현재 종가로 매번 파생한다.</b>
 *
 * <p>한 번 뚫린 대는 역할이 전환된다 — 저항이었던 자리가 지지가 된다. 이것을 돌파 감지 후
 * 플래그를 뒤집는 방식으로 구현하면 "언제 뚫린 것으로 보는가"(종가인가 고가인가, 몇 봉을
 * 유지해야 하는가) 라는 두 번째 정의가 필요해지고, 그 상태가 재실행 사이에 남으면 결정론이
 * 깨진다. 파생값이면 그 질문 자체가 사라진다.
 */
public enum ZoneRole {

    /** 가격 아래에 있다. 내려와 닿으면 받친다 → 롱. */
    SUPPORT(Direction.LONG),

    /** 가격 위에 있다. 올라가 닿으면 막는다 → 숏. */
    RESISTANCE(Direction.SHORT);

    private final Direction entryDirection;

    ZoneRole(Direction entryDirection) {
        this.entryDirection = entryDirection;
    }

    /** 반대 역할. 롱의 익절 목표는 저항, 숏의 익절 목표는 지지다. */
    public ZoneRole opposite() {
        return this == SUPPORT ? RESISTANCE : SUPPORT;
    }

    /** 기록용 한글 이름. {@code MarketContext.rationale} 에 실린다. */
    public String label() {
        return this == SUPPORT ? "지지" : "저항";
    }

    /**
     * 대 대비 종가의 위치가 역할을 정한다.
     *
     * <p>{@link BandPosition#INSIDE} 는 역할이 없다 — 가격이 대 안에 있으면 어느 경계가
     * 근단인지 정해지지 않는다. 경계 위도 마찬가지다({@code PriceBand} 는 경계를 구간에
     * 포함시킨다).
     */
    public static Optional<ZoneRole> of(BandPosition closePosition) {
        return switch (closePosition) {
            case ABOVE -> Optional.of(SUPPORT);
            case BELOW -> Optional.of(RESISTANCE);
            case INSIDE -> Optional.empty();
        };
    }

    /** 이 역할의 대에 닿았을 때 거는 방향. 반전 진입이므로 지지는 롱, 저항은 숏이다. */
    public Direction entryDirection() {
        return entryDirection;
    }
}
