package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.util.UUID;

/**
 * 거래 한 건의 식별자.
 *
 * <p>DB 시퀀스가 아니라 UUID 인 이유는 <b>계획이 저장되기 전에 식별자가 필요하기</b> 때문이다.
 * 계획 → 체결 → 청산은 세 번의 쓰기이고, 첫 쓰기 이전에 도메인이 거래를 만들 수 있어야
 * 테스트가 DB 없이 생애주기 전체를 돌 수 있다.
 */
public record TradeId(UUID value) {

    public TradeId {
        DomainValues.required(value, "거래 식별자");
    }

    /** 새 거래의 식별자. 난수원은 도메인 밖의 관심사가 아니므로 여기서 만든다. */
    public static TradeId random() {
        return new TradeId(UUID.randomUUID());
    }

    public static TradeId of(String value) {
        DomainValues.required(value, "거래 식별자");
        try {
            return new TradeId(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            throw new InvalidValueException("거래 식별자가 UUID 형식이 아니다: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
