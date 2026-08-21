package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 펀딩비·미결제약정·롱숏비율은 진입 시점의 시장 상태를 한 장으로 묶은 것이다.
 * Phase 5 의 {@code MarketContext} 가 이 값을 그대로 기록하게 된다.
 */
class MarketMetricsTest {

    private static final Instant AT = Instant.parse("2026-08-21T08:00:00Z");

    private static MarketMetrics metrics(String ratio) {
        return new MarketMetrics(Symbol.BTC_USDT, AT, FundingRate.ofPercent("0.01"),
                Quantity.of("81234.5"), new BigDecimal(ratio));
    }

    @Test
    void 세_지표를_한_시점으로_묶는다() {
        MarketMetrics m = metrics("1.8342");

        assertThat(m.symbol()).isEqualTo(Symbol.BTC_USDT);
        assertThat(m.at()).isEqualTo(AT);
        assertThat(m.openInterest()).isEqualTo(Quantity.of("81234.5"));
        assertThat(m.longShortRatio()).isEqualByComparingTo("1.8342");
    }

    /** 롱숏비율은 스케일 4 로 고정된다. 소스마다 자릿수가 달라도 같은 값은 같게 읽혀야 한다. */
    @Test
    void 롱숏비율의_스케일은_4로_고정된다() {
        assertThat(metrics("1.83").longShortRatio()).isEqualTo(new BigDecimal("1.8300"));
    }

    @Test
    void 롱숏비율이_0_이하면_거부된다() {
        assertThatThrownBy(() -> metrics("0")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> metrics("-1")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new MarketMetrics(null, AT, FundingRate.ofPercent("0.01"),
                Quantity.of("1"), BigDecimal.ONE)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketMetrics(Symbol.BTC_USDT, null,
                FundingRate.ofPercent("0.01"), Quantity.of("1"), BigDecimal.ONE))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketMetrics(Symbol.BTC_USDT, AT, null,
                Quantity.of("1"), BigDecimal.ONE)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketMetrics(Symbol.BTC_USDT, AT,
                FundingRate.ofPercent("0.01"), null, BigDecimal.ONE))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new MarketMetrics(Symbol.BTC_USDT, AT,
                FundingRate.ofPercent("0.01"), Quantity.of("1"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
