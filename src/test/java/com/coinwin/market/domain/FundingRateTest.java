package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 펀딩비가 {@code common.Percentage} 가 아닌 이유는 하나다 — <b>음수가 정상값이다.</b>
 * 숏이 우세하면 숏이 롱에게 낸다. {@code Percentage} 는 음수를 금지하므로 쓸 수 없다.
 *
 * <p>부호가 곧 정보다. 부호를 잃으면 "누가 누구에게 내는가" 가 사라진다.
 */
class FundingRateTest {

    /** 바이낸스는 비율(0.0001)로 준다. 사람이 읽는 단위는 백분율(0.01%) 이다. */
    @Test
    void 비율로_들어온_값을_백분율로_바꾼다() {
        assertThat(FundingRate.ofFraction(new BigDecimal("0.0001")))
                .isEqualTo(FundingRate.ofPercent("0.01"));
    }

    @Test
    void 백분율을_다시_비율로_되돌린다() {
        assertThat(FundingRate.ofPercent("0.01").asFraction())
                .isEqualByComparingTo("0.0001");
    }

    @Test
    void 음수_펀딩비가_성립한다() {
        FundingRate rate = FundingRate.ofPercent("-0.0125");

        assertThat(rate.isNegative()).isTrue();
        assertThat(rate.value()).isEqualByComparingTo("-0.0125");
    }

    @Test
    void 양수_펀딩비는_음수가_아니다() {
        assertThat(FundingRate.ofPercent("0.01").isNegative()).isFalse();
        assertThat(FundingRate.ofPercent("0").isNegative()).isFalse();
    }

    /** 스케일 6 은 비율 스케일 8 에 대응한다. 바이낸스가 주는 자릿수를 잃지 않는다. */
    @Test
    void 스케일은_6으로_고정된다() {
        assertThat(FundingRate.ofPercent("0.01").value()).isEqualTo(new BigDecimal("0.010000"));
        assertThat(FundingRate.ofFraction(new BigDecimal("0.00012345")).value())
                .isEqualTo(new BigDecimal("0.012345"));
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> FundingRate.ofPercent(null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> FundingRate.ofFraction(null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 숫자가_아닌_문자열은_거부된다() {
        assertThatThrownBy(() -> FundingRate.ofPercent("없음"))
                .isInstanceOf(InvalidValueException.class);
    }
}
