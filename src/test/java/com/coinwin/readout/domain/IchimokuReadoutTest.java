package com.coinwin.readout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.IchimokuValue;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일목 판독 — 구름 위치 하나로 접히던 것들.
 *
 * <p><b>재는 것은 정의를 따르는가까지다.</b> "구름이 두꺼우니 안 뚫린다" 는 이 코드가 말하지
 * 않으므로 잴 것도 없다. 근거는 {@code docs/spec/indicator-usage.md} § 4.1.
 */
class IchimokuReadoutTest {

    private static final Money ATR = Money.of(new BigDecimal("500"));

    @Test
    @DisplayName("전환선이 기준선 위면 간격이 양수다")
    void 전환_기준_간격() {
        IchimokuReadout 판독 = 판독(일목(60500, 60000, 59000, 58000), 61000);

        assertThat(판독.conversionGap().value()).isEqualByComparingTo("1.00");
    }

    /** 뺀 순서가 부호를 정한다. 뒤집히면 부호도 뒤집혀야 한다. */
    @Test
    @DisplayName("전환선이 기준선 아래면 간격이 음수다")
    void 전환선이_아래면() {
        assertThat(판독(일목(59500, 60000, 59000, 58000), 61000).conversionGap().value())
                .isEqualByComparingTo("-1.00");
    }

    @Test
    @DisplayName("기준선까지 거리는 종가에서 잰다")
    void 기준선까지() {
        assertThat(판독(일목(60500, 60000, 59000, 58000), 61000).baseLineGap().value())
                .isEqualByComparingTo("2.00");
    }

    /**
     * <b>두께는 언제나 0 이상이다.</b> 어느 선행스팬이 위인지는 {@link
     * IchimokuReadout#bullishCloud()} 가 말하므로, 두께까지 부호를 가지면 같은 사실이 두 칸에
     * 실린다.
     */
    @Test
    @DisplayName("구름 두께는 뒤집혀도 0 이상이다")
    void 구름_두께() {
        assertThat(판독(일목(60500, 60000, 59000, 58000), 61000).cloudThickness().value())
                .isEqualByComparingTo("2.00");
        assertThat(판독(일목(60500, 60000, 58000, 59000), 61000).cloudThickness().value())
                .isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("선행스팬 1 이 2 위면 상승 구름이다")
    void 구름_방향() {
        assertThat(판독(일목(60500, 60000, 59000, 58000), 61000).bullishCloud()).isTrue();
        assertThat(판독(일목(60500, 60000, 58000, 59000), 61000).bullishCloud()).isFalse();
    }

    /**
     * <b>위치는 지표가 판정한다.</b> 여기서 가격과 선을 직접 비교하면 "경계는 구간에 포함된다"
     * 는 규칙이 두 곳에 생긴다.
     */
    @Test
    @DisplayName("구름 위치는 지표가 낸 것을 그대로 옮긴다")
    void 위치() {
        assertThat(판독(일목(60500, 60000, 59000, 58000), 61000).position())
                .isEqualTo(BandPosition.ABOVE);
        assertThat(판독(일목(60500, 60000, 59000, 58000), 58500).position())
                .isEqualTo(BandPosition.INSIDE);
    }

    /**
     * 값 만들기와 읽기를 나눈다. 파라미터 한계(넷)가 그렇게 하게 만들었는데, 나눠 놓으니
     * 어느 넷이 일목의 선이고 무엇이 지금 가격인지가 호출부에서 읽힌다.
     */
    private static IchimokuReadout 판독(IchimokuValue value, int close) {
        return IchimokuReadout.of(value, 가격(close), ATR);
    }

    private static IchimokuValue 일목(int conversion, int base, int spanA, int spanB) {
        return new IchimokuValue(
                가격(conversion), 가격(base), 가격(spanA), 가격(spanB), Optional.empty());
    }

    private static Price 가격(int value) {
        return Price.of(BigDecimal.valueOf(value));
    }
}
