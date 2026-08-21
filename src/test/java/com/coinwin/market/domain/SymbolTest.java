package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import org.junit.jupiter.api.Test;

/**
 * 심볼은 저장 키의 일부다({@code symbol, interval, open_time}). 표기가 흔들리면 같은 종목이
 * 두 줄로 저장되고 그 순간 "중복 없음" 이 무의미해진다. 그래서 생성 시점에 표기를 고정한다.
 */
class SymbolTest {

    @Test
    void 소문자로_들어와도_대문자로_고정된다() {
        assertThat(Symbol.of("btcusdt")).isEqualTo(Symbol.of("BTCUSDT"));
    }

    @Test
    void 앞뒤_공백은_제거된다() {
        assertThat(Symbol.of("  BTCUSDT  ").value()).isEqualTo("BTCUSDT");
    }

    @Test
    void 영숫자가_아닌_문자는_거부된다() {
        assertThatThrownBy(() -> Symbol.of("BTC-USDT"))
                .isInstanceOf(InvalidValueException.class)
                .hasMessageContaining("BTC-USDT");
    }

    @Test
    void 빈_문자열은_거부된다() {
        assertThatThrownBy(() -> Symbol.of("   ")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> Symbol.of(null)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 이_프로젝트가_다루는_종목은_BTCUSDT_하나다() {
        assertThat(Symbol.BTC_USDT.value()).isEqualTo("BTCUSDT");
    }
}
