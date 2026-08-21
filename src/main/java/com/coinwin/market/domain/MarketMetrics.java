package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 한 시점의 시장 상태 — 펀딩비, 미결제약정, 롱숏 계정 비율.
 *
 * <p>세 값의 출처가 각각 다른 엔드포인트지만 <b>진입 판단에서는 함께 읽힌다.</b> 따로 내보내면
 * 서로 다른 시각의 값을 나란히 보게 되는 일이 생긴다. 시각을 하나로 묶어 그것을 막는다.
 *
 * <p>{@code longShortRatio} 만 {@link BigDecimal} 인 이유는 손익비와 같다 — 무차원 비(比)라
 * {@code Percentage} 도 {@code Money} 도 아니다. 대신 스케일 4 로 고정한다.
 */
public record MarketMetrics(
        Symbol symbol,
        Instant at,
        FundingRate fundingRate,
        Quantity openInterest,
        BigDecimal longShortRatio) {

    private static final int RATIO_SCALE = 4;

    public MarketMetrics {
        DomainValues.required(symbol, "종목");
        DomainValues.required(at, "관측 시각");
        DomainValues.required(fundingRate, "펀딩비");
        DomainValues.required(openInterest, "미결제약정");
        longShortRatio = DomainValues.scaled(longShortRatio, RATIO_SCALE, "롱숏비율");
        if (longShortRatio.signum() <= 0) {
            throw new InvalidValueException("롱숏비율은 0보다 커야 한다: " + longShortRatio);
        }
    }
}
