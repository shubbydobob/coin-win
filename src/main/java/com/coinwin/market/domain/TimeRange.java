package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Instant;

/**
 * 조회 구간. <b>반열림</b> {@code [from, to)} 이다.
 *
 * <p>양끝을 다 포함하면 연속한 두 구간을 이어 받을 때 경계의 캔들이 두 번 온다. 그것이 그대로
 * "증분 저장 중복" 이 된다. 어댑터마다 경계 처리를 되풀이하는 대신 구간 정의에서 막는다.
 */
public record TimeRange(Instant from, Instant to) {

    public TimeRange {
        DomainValues.required(from, "조회 시작 시각");
        DomainValues.required(to, "조회 끝 시각");
        if (!from.isBefore(to)) {
            throw new InvalidMarketDataException(
                    "조회 구간의 끝은 시작보다 뒤여야 한다: %s ~ %s".formatted(from, to));
        }
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(from) && moment.isBefore(to);
    }
}
