package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 24시간 요약 — 지금 얼마이고, 하루 동안 어디까지 갔었나.
 *
 * <p><b>변동률에 {@code Percentage} 를 쓰지 않는다.</b> 그 값 객체는 음수를 금지하는데 하락은
 * 정상값이고, 부호를 잃으면 오른 날과 내린 날이 구분되지 않는다. {@code FundingRate} 가 같은
 * 이유로 같은 선택을 했다. 스케일 6 도 거기에 맞춘다.
 *
 * <p>거래소는 이 값을 <b>이미 퍼센트로</b> 준다({@code priceChangePercent: "-1.009"}).
 * 100 을 곱하지 않는다.
 *
 * <p>현재가가 하루 고저 범위 밖이면 거부한다. 그 조합은 응답을 잘못 읽었다는 뜻이고, 담아 두면
 * 화면이 "최고가보다 높은 현재가" 를 태연히 보여 준다.
 */
public record Ticker(
        Symbol symbol,
        Price last,
        BigDecimal change24hPercent,
        Price high24h,
        Price low24h,
        Quantity volume24h,
        Instant at) {

    /** {@code FundingRate} 와 같은 자릿수. 둘 다 부호 있는 백분율이다. */
    private static final int PERCENT_SCALE = 6;

    public Ticker {
        DomainValues.required(symbol, "종목");
        DomainValues.required(last, "현재가");
        DomainValues.required(high24h, "24시간 최고가");
        DomainValues.required(low24h, "24시간 최저가");
        DomainValues.required(volume24h, "24시간 거래량");
        DomainValues.required(at, "관측 시각");
        change24hPercent = DomainValues.scaled(change24hPercent, PERCENT_SCALE, "24시간 변동률");
        if (high24h.isBelow(low24h)) {
            throw new InvalidValueException("24시간 최저가는 최고가보다 높을 수 없다");
        }
        if (last.isBelow(low24h) || high24h.isBelow(last)) {
            throw new InvalidValueException("현재가가 24시간 고저 범위 밖이다: " + last.value());
        }
    }
}
