package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 거래 종목. {@code BTCUSDT} 처럼 대문자 영숫자로만 표기한다.
 *
 * <p>표기를 생성 시점에 고정하는 이유는 이것이 <b>저장 키의 일부</b>이기 때문이다.
 * {@code (symbol, interval, open_time)} 이 캔들의 식별자인데 {@code btcusdt} 와
 * {@code BTCUSDT} 가 다른 키가 되면 같은 캔들이 두 줄로 남는다.
 */
public record Symbol(String value) {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Z0-9]{1,20}$");

    /** 이 프로젝트가 다루는 유일한 종목. 근거: .claude/docs/scope.md "BTC 무기한 선물". */
    public static final Symbol BTC_USDT = new Symbol("BTCUSDT");

    public Symbol {
        value = DomainValues.required(value, "종목").trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidValueException("종목 표기가 올바르지 않다: " + value);
        }
    }

    public static Symbol of(String value) {
        return new Symbol(value);
    }
}
