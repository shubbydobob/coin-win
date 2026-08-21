package com.coinwin.position.domain;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 이 거래 한 건에 걸 수 있는 돈. 계좌 잔고와 그중 몇 퍼센트를 걸지로 표현한다.
 *
 * <p>{@code riskAmount} 를 직접 받지 않고 잔고와 비율로 받는 이유는, 잔고 대비 얼마를 걸고
 * 있는지가 계획 검토에서 실제로 보고 싶은 값이기 때문이다. 금액만 있으면 그 판단이 사라진다.
 */
public record RiskBudget(Money accountBalance, Percentage riskPercent) {

    public RiskBudget {
        PositionValues.required(accountBalance, "계좌 잔고");
        PositionValues.required(riskPercent, "리스크 비율");
        if (accountBalance.value().signum() <= 0) {
            throw new InvalidValueException(
                    "계좌 잔고는 0 보다 커야 한다: " + accountBalance.value().toPlainString());
        }
        Money risk = riskPercent.applyTo(accountBalance);
        if (risk.isGreaterThan(accountBalance)) {
            throw new RiskExceedsBalanceException(risk, accountBalance);
        }
    }

    public static RiskBudget of(String accountBalance, String riskPercent) {
        return new RiskBudget(Money.of(accountBalance), Percentage.of(riskPercent));
    }

    /** 손절에 걸렸을 때 잃기로 한 금액. 포지션 사이징의 출발점이다. */
    public Money riskAmount() {
        return riskPercent.applyTo(accountBalance);
    }
}
