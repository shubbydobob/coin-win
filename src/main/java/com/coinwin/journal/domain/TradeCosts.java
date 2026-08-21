package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;

/**
 * 거래 한 건에 실제로 나간 비용. 도메인이 계산할 수 없어 <b>사람이 적어 넣는</b> 유일한 금액이다.
 *
 * <p>수수료는 체결가·수량·거래소 등급으로 결정되고, 펀딩비는 8시간마다의 요율과 보유 기간으로
 * 결정된다. 둘 다 거래소가 정하며 우리가 재현할 근거가 없다. 손익을 통째로 입력받는 대신
 * 비용만 입력받는 이유가 그것이다 — <b>재현할 수 있는 것은 도메인이 계산하고, 없는 것만 받는다.</b>
 *
 * <p>펀딩비는 <b>음수가 정상값</b>이다. 포지션 방향이 소수 쪽이면 받는다. 부호를 버리면
 * "누가 누구에게 냈는가" 가 사라진다 — {@code market.domain} 의 {@code FundingRate} 와 같은 이유다.
 */
public record TradeCosts(Money fees, Money funding) {

    public TradeCosts {
        DomainValues.required(fees, "수수료");
        DomainValues.required(funding, "펀딩비");
        if (fees.isNegative()) {
            throw new InvalidValueException("수수료는 음수일 수 없다: " + fees.value());
        }
    }

    /** 비용이 없는 거래. 테스트와 비용을 아직 적지 않은 기록에 쓴다. */
    public static TradeCosts none() {
        return new TradeCosts(Money.of("0"), Money.of("0"));
    }

    public static TradeCosts of(String fees, String funding) {
        return new TradeCosts(Money.of(fees), Money.of(funding));
    }

    public Money total() {
        return fees.plus(funding);
    }
}
