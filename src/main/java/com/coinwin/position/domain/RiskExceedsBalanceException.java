package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;

/**
 * 한 번의 거래에 걸겠다는 금액이 계좌 잔고를 넘을 때 던진다.
 *
 * <p>비율까지 함께 싣는 이유는 경계값 때문이다. 100.0001% 는 금액으로 옮기면 스케일 2 에서
 * 잔고와 같은 숫자로 찍혀, 금액만 보여주면 "800.00 이 800.00 을 초과한다" 가 된다.
 */
public class RiskExceedsBalanceException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RiskExceedsBalanceException(
            Percentage riskPercent, Money riskAmount, Money accountBalance) {
        super("리스크 비율 %s%% 는 잔고를 넘는다: 리스크 금액 %s / 계좌 잔고 %s"
                .formatted(riskPercent.value().toPlainString(),
                        riskAmount.value().toPlainString(),
                        accountBalance.value().toPlainString()));
    }
}
