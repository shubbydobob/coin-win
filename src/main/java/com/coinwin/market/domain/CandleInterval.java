package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.time.Duration;
import java.util.Arrays;

/**
 * 캔들 주기.
 *
 * <p>{@code "1h"} 같은 코드 문자열을 도메인이 들고 있는 것은 HTTP 를 아는 것과 다르다.
 * 이 코드는 바이낸스 질의 파라미터이자 <b>저장 키의 일부</b>이고, 어댑터 세 곳이 같은 표기를
 * 쓰지 않으면 같은 캔들이 서로 다른 키로 두 번 저장된다. 표기의 단일 출처가 여기다.
 */
public enum CandleInterval {

    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    FIVE_MINUTES("5m", Duration.ofMinutes(5)),
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15)),
    ONE_HOUR("1h", Duration.ofHours(1)),
    FOUR_HOURS("4h", Duration.ofHours(4)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String code;
    private final Duration length;

    CandleInterval(String code, Duration length) {
        this.code = code;
        this.length = length;
    }

    public String code() {
        return code;
    }

    /**
     * 한 캔들이 덮는 시간. 페이지를 이어 받을 때 다음 시작 시각이
     * {@code 마지막 openTime + length} 다.
     */
    public Duration length() {
        return length;
    }

    public static CandleInterval ofCode(String code) {
        String wanted = DomainValues.required(code, "캔들 주기");
        return Arrays.stream(values())
                .filter(interval -> interval.code.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new InvalidValueException("지원하지 않는 캔들 주기다: " + wanted));
    }
}
