package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.position.domain.RiskBudget;

/**
 * 계좌 쪽 설정. 전략이 아니라 <b>얼마를 걸 것인가</b>를 정한다.
 *
 * @param initialCapital 시작 자본
 * @param riskPercent 거래당 걸 비율
 * @param leverage 레버리지. 증거금과 청산가에만 영향을 준다 — 손실 크기는 손절가가 정한다
 * @param capitalMode 사이징의 기준 잔고
 */
public record AccountSettings(
        Money initialCapital, Percentage riskPercent, int leverage, CapitalMode capitalMode) {

    public AccountSettings {
        DomainValues.required(initialCapital, "초기 자본");
        DomainValues.required(riskPercent, "거래당 리스크 비율");
        DomainValues.required(capitalMode, "잔고 모드");
        if (!initialCapital.isGreaterThan(Money.of("0"))) {
            throw new InvalidBacktestException(
                    "초기 자본은 0 보다 커야 한다: " + initialCapital.value().toPlainString());
        }
        if (leverage < 1) {
            throw new InvalidBacktestException("레버리지는 1 이상이어야 한다: " + leverage);
        }
    }

    /** 이 거래의 사이징 기준 잔고. 모드가 정한다. */
    public Money balanceFor(Money currentEquity) {
        DomainValues.required(currentEquity, "현재 자산");
        return switch (capitalMode) {
            case FIXED -> initialCapital;
            case COMPOUND -> currentEquity;
        };
    }

    public RiskBudget budgetFor(Money currentEquity) {
        return new RiskBudget(balanceFor(currentEquity), riskPercent);
    }

    /**
     * 이 자산으로 거래를 이어 갈 수 있는가.
     *
     * <p>고정 모드에서도 <b>자산 자체</b>를 본다. 사이징은 초기 자본으로 하더라도 계좌가 비면
     * 거래가 성립하지 않기 때문이다. 물어보고 멈추는 이유는 {@code RiskBudget} 이 예외를
     * 던지게 두면 백테스트가 중간에 죽고 그때까지의 결과마저 사라지기 때문이다.
     */
    public boolean canTradeWith(Money currentEquity) {
        DomainValues.required(currentEquity, "현재 자산");
        return currentEquity.isGreaterThan(Money.of("0"));
    }
}
