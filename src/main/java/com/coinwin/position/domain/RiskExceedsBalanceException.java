package com.coinwin.position.domain;

import com.coinwin.common.domain.DomainException;
import com.coinwin.common.domain.Money;

/** 한 번의 거래에 걸겠다는 금액이 계좌 잔고를 넘을 때 던진다. */
public class RiskExceedsBalanceException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RiskExceedsBalanceException(Money riskAmount, Money accountBalance) {
        super("리스크 금액(%s)이 계좌 잔고(%s)를 초과한다"
                .formatted(riskAmount.value().toPlainString(),
                        accountBalance.value().toPlainString()));
    }
}
