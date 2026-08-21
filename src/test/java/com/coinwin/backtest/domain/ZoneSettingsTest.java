package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZoneSettingsTest {

    /**
     * 허용치가 ATR 에 매달려 있는 것이 이 설정의 요점이다. 고정 백분율이면 15분봉과 일봉에
     * 같은 값을 쓸 수 없어 주기마다 재튜닝해야 한다.
     */
    @Test
    void 군집_허용치는_그_시점의_ATR_에_배수를_곱한_값이다() {
        ZoneSettings settings = new ZoneSettings(5, new BigDecimal("0.5"), 2, 14);

        assertThat(settings.toleranceFor(Money.of("280"))).isEqualTo(Money.of("140"));
        assertThat(settings.toleranceFor(Money.of("40"))).isEqualTo(Money.of("20"));
    }

    @Test
    void 기본값은_피벗_5봉_군집_0_5배_터치_2회_ATR_14다() {
        ZoneSettings standard = ZoneSettings.standard();

        assertThat(standard.pivotLookback()).isEqualTo(5);
        assertThat(standard.clusterMultiple()).isEqualByComparingTo("0.5");
        assertThat(standard.minTouches()).isEqualTo(2);
        assertThat(standard.atrPeriod()).isEqualTo(14);
    }

    @Test
    void 군집_배수는_음수일_수_없다() {
        assertThatThrownBy(() -> new ZoneSettings(5, new BigDecimal("-0.1"), 2, 14))
                .isInstanceOf(InvalidBacktestException.class)
                .hasMessageContaining("군집");
    }

    /** 터치 1회짜리 대를 만들 수 있으면 "피벗 하나는 선이지 대가 아니다" 가 무너진다. */
    @Test
    void 최소_터치는_2_미만일_수_없다() {
        assertThatThrownBy(() -> new ZoneSettings(5, BigDecimal.ONE, 1, 14))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 봉_수는_1_이상이어야_한다() {
        assertThatThrownBy(() -> new ZoneSettings(0, BigDecimal.ONE, 2, 14))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ZoneSettings(5, BigDecimal.ONE, 2, 0))
                .isInstanceOf(InvalidValueException.class);
    }
}
