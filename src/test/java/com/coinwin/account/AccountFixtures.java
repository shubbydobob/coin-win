package com.coinwin.account;

import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.Optional;

/**
 * 거래소 포지션 픽스처.
 *
 * <p>평단·청산가는 {@code JournalFixtures} 의 시나리오와 맞춘다 — 롱 평단 59500, 숏 평단
 * 60500. 대조 테스트가 보는 것은 방향과 수량이지만, 두 픽스처의 숫자가 어긋나 있으면 화면을
 * 손으로 확인할 때 무엇이 이상한지 판단할 기준이 없어진다.
 */
public final class AccountFixtures {

    public static final Instant OBSERVED_AT = Instant.parse("2026-08-23T01:53:00Z");

    private AccountFixtures() {
    }

    /** 롱 포지션. 평단 59500 — 기록 픽스처의 2분할 평단과 같다. */
    public static ExchangePosition longPosition(String quantity, Instant observedAt) {
        return new ExchangePosition(Symbol.BTC_USDT, Direction.LONG,
                Quantity.of(quantity), Price.of("59500"), Optional.of(Price.of("53765.06")),
                Money.of("12.40"), observedAt);
    }

    /** 숏 포지션. 평단 60500 — 기록 픽스처의 숏 2분할 평단과 같다. */
    public static ExchangePosition shortPosition(String quantity, Instant observedAt) {
        return new ExchangePosition(Symbol.BTC_USDT, Direction.SHORT,
                Quantity.of(quantity), Price.of("60500"), Optional.of(Price.of("66043.21")),
                Money.of("-8.10"), observedAt);
    }

    public static ExchangePosition longPosition(String quantity) {
        return longPosition(quantity, OBSERVED_AT);
    }
}
