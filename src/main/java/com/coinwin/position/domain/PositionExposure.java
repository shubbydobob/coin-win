package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 실제로 열린 포지션의 크기. 청산가는 이 넷과 유지증거금 규칙만으로 결정된다.
 *
 * <pre>
 * LONG :  liq = [EP × (1 - 1/lev) - deduction/qty] / (1 - MMR)
 * SHORT:  liq = [EP × (1 + 1/lev) + deduction/qty] / (1 + MMR)
 * </pre>
 *
 * <p><b>왜 나눗셈인가.</b> 유지증거금은 진입 명목가가 아니라 <b>청산가 기준 명목가</b>
 * ({@code qty × P × MMR})로 계산된다. 미지수 {@code P} 가 양변에 있으므로 정리하면 나눗셈이
 * 남는다. Phase 1 의 근사식 {@code EP × (1 - 1/lev + MMR)} 은 그 나눗셈을 곱셈 한 번으로
 * 뭉갠 것이고, 그래서 0.04% 대의 오차가 남았다. 근거는 {@code docs/adr/012}.
 *
 * <p>계획이 아니라 <b>체결된 크기</b>에 매달린 이유는 {@link FillState} 와 같다 — 분할 진입
 * 계획에는 단일한 청산가가 존재하지 않는다.
 */
public record PositionExposure(
        Direction direction,
        Price averageEntryPrice,
        Quantity quantity,
        int leverage) {

    public PositionExposure {
        DomainValues.required(direction, "방향");
        DomainValues.required(averageEntryPrice, "평단");
        DomainValues.required(quantity, "수량");
        if (leverage < 1) {
            throw new InvalidValueException("레버리지는 1 이상이어야 한다: " + leverage);
        }
        if (quantity.value().signum() == 0) {
            throw new InvalidPositionPlanException("수량이 0 인 포지션의 청산가는 성립하지 않는다");
        }
    }

    /** 명목가. 어느 레버리지 구간에 드는지를 이 값이 결정한다. */
    public Money notional() {
        return quantity.times(averageEntryPrice.asAmount());
    }

    public Price liquidationPrice(MaintenanceMargin margin) {
        BigDecimal mmr = DomainValues.required(margin, "유지증거금 규칙").rate().asFraction();
        BigDecimal perUnitDeduction = margin.deduction().value()
                .divide(quantity.value(), MathContext.DECIMAL128);
        BigDecimal entry = averageEntryPrice.value();
        BigDecimal inverseLeverage = inverseLeverage();
        return switch (direction) {
            case LONG -> quotient(
                    entry.multiply(BigDecimal.ONE.subtract(inverseLeverage))
                            .subtract(perUnitDeduction),
                    BigDecimal.ONE.subtract(mmr));
            case SHORT -> quotient(
                    entry.multiply(BigDecimal.ONE.add(inverseLeverage)).add(perUnitDeduction),
                    BigDecimal.ONE.add(mmr));
        };
    }

    private BigDecimal inverseLeverage() {
        return BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), MathContext.DECIMAL64);
    }

    /**
     * 스냅은 마지막 한 번만 한다. 분자를 먼저 {@link Price} 로 만들면 스케일 2 로 잘린 값을
     * 나누게 되어 결과가 어긋난다.
     */
    private static Price quotient(BigDecimal numerator, BigDecimal denominator) {
        return Price.of(numerator.divide(denominator, MathContext.DECIMAL64));
    }
}
