package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 거시 자산의 표기. {@code QQQUSDT} 도 {@code ^GSPC} 도 {@code NQ=F} 도 여기 들어온다.
 *
 * <p><b>{@link Symbol} 을 쓰지 않는 이유가 이 타입의 존재 이유다.</b> 그쪽은
 * {@code [A-Z0-9]{1,20}} 만 받는데, 그 규칙이 까다로워서가 아니라 <b>그것이 캔들의 저장 키</b>
 * 이기 때문이다 — {@code (symbol, interval, open_time)} 이 식별자다. 야후 티커는 저장되지도
 * 조회되지도 않고 화면에 한 줄 뜨는 이름일 뿐이라 같은 타입에 넣으면 안 된다.
 *
 * <p>실제로 {@code Symbol.of("^GSPC")} 가 거절하면서 이 구분이 드러났다. <b>타입이 옳았고</b>
 * 규칙을 느슨하게 하는 대신 종류를 나눴다.
 *
 * <p>허용하는 글자에 {@code ^ = . -} 가 있는 것은 야후 표기 때문이다 — 지수는 {@code ^},
 * 선물은 {@code =F}, 달러지수는 {@code DX-Y.NYB} 다.
 */
public record MacroTicker(String value) {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Z0-9^=.-]{1,20}$");

    public MacroTicker {
        value = DomainValues.required(value, "거시 표기").trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidValueException("거시 표기가 올바르지 않다: " + value);
        }
    }

    public static MacroTicker of(String value) {
        return new MacroTicker(value);
    }
}
