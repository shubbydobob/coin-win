package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 무기한 선물의 펀딩비. 백분율 표기, 스케일 6, <b>음수를 허용한다.</b>
 *
 * <p>{@code common.Percentage} 를 쓰지 않는 이유는 하나다 — 음수가 정상값이다. 숏이 우세하면
 * 펀딩비가 음수가 되고 숏이 롱에게 낸다. {@code Percentage} 는 음수를 금지하므로 여기에
 * 쓸 수 없고, 부호를 잃으면 "누가 누구에게 내는가" 라는 정보 자체가 사라진다.
 *
 * <p>스케일 6(백분율)은 바이낸스가 주는 비율 스케일 8 에 대응한다. 0.00012345 → 0.012345%.
 */
public record FundingRate(BigDecimal value) {

    private static final int SCALE = 6;
    private static final int FRACTION_SCALE = SCALE + 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String LABEL = "펀딩비";

    public FundingRate {
        value = DomainValues.scaled(value, SCALE, LABEL);
    }

    /** 사람이 읽는 단위. {@code "0.01"} 은 0.01% 다. */
    public static FundingRate ofPercent(String percent) {
        return new FundingRate(DomainValues.decimal(percent, LABEL));
    }

    /** 바이낸스가 주는 단위. {@code 0.0001} 은 0.01% 다. */
    public static FundingRate ofFraction(BigDecimal fraction) {
        return new FundingRate(DomainValues.required(fraction, LABEL).multiply(HUNDRED));
    }

    public BigDecimal asFraction() {
        return value.divide(HUNDRED, FRACTION_SCALE, RoundingMode.HALF_UP);
    }

    /** 음수면 숏이 롱에게 낸다. */
    public boolean isNegative() {
        return value.signum() < 0;
    }
}
