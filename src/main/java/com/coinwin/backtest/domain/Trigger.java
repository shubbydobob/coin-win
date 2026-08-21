package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.position.domain.Direction;
import java.util.Optional;

/**
 * 한 봉 안에서 특정 가격에 닿았는가, 닿았다면 얼마에 체결됐는가.
 *
 * <p>진입 지정가·손절·익절이 전부 이 형태다. 규칙은 하나다 —
 * <b>봉이 트리거를 이미 지나쳐서 열렸으면 시가에, 아니면 트리거 가격에 체결된다.</b>
 *
 * <p>갭을 시가로 잡는 것이 핵심이다. 손절가를 뛰어넘어 열린 봉에서 손절가에 체결됐다고 보면
 * 백테스트가 실제보다 좋게 나오고, 지정가를 지나쳐 열린 봉에서 지정가에 체결됐다고 보면
 * 진입가가 실제보다 나쁘게 나온다. 둘 다 같은 규칙 하나로 맞는다.
 */
public record Trigger(Price price, Approach approach) {

    /** 가격이 어느 쪽에서 다가와야 닿는가. */
    public enum Approach {
        /** 내려와서 닿는다. 롱 지정가·롱 손절·숏 익절. */
        FALLING,
        /** 올라가서 닿는다. 숏 지정가·숏 손절·롱 익절. */
        RISING
    }

    public Trigger {
        DomainValues.required(price, "트리거 가격");
        DomainValues.required(approach, "접근 방향");
    }

    /** 롱이면 하락해서, 숏이면 상승해서 닿는다. <b>진입가와 손절가</b>가 여기 속한다. */
    public static Trigger adverse(Direction direction, Price price) {
        DomainValues.required(direction, "포지션 방향");
        return new Trigger(price, switch (direction) {
            case LONG -> Approach.FALLING;
            case SHORT -> Approach.RISING;
        });
    }

    /** 롱이면 상승해서, 숏이면 하락해서 닿는다. <b>익절가</b>다. */
    public static Trigger benign(Direction direction, Price price) {
        DomainValues.required(direction, "포지션 방향");
        return new Trigger(price, switch (direction) {
            case LONG -> Approach.RISING;
            case SHORT -> Approach.FALLING;
        });
    }

    /** 이 봉에서 체결됐다면 체결가. 닿지 않았으면 비어 있다. */
    public Optional<Price> fillIn(Candle candle) {
        DomainValues.required(candle, "캔들");
        return switch (approach) {
            case FALLING -> filled(candle.low().isAbove(price), candle.open().isBelow(price),
                    candle.open());
            case RISING -> filled(candle.high().isBelow(price), candle.open().isAbove(price),
                    candle.open());
        };
    }

    private Optional<Price> filled(boolean untouched, boolean gapped, Price open) {
        if (untouched) {
            return Optional.empty();
        }
        return Optional.of(gapped ? open : price);
    }
}
