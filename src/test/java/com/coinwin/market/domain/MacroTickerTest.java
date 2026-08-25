package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 거시 표기.
 *
 * <p><b>{@link Symbol} 과 갈라 둔 것이 이 테스트의 요점이다.</b> 저쪽은 캔들의 저장 키라
 * {@code [A-Z0-9]} 만 받는데, 야후 표기는 {@code ^ = . -} 를 쓴다. 규칙을 느슨하게 하는 대신
 * 종류를 나눴고, 그 경계가 지켜지는지를 여기서 본다.
 */
class MacroTickerTest {

    @ParameterizedTest
    @ValueSource(strings = {"QQQUSDT", "^GSPC", "NQ=F", "DX-Y.NYB", "BTCUSDT"})
    @DisplayName("바이낸스 표기도 야후 표기도 받는다")
    void 두_출처의_표기(String value) {
        assertThat(MacroTicker.of(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("소문자는 대문자로 맞춘다")
    void 대문자로_맞춘다() {
        assertThat(MacroTicker.of("  nq=f  ").value()).isEqualTo("NQ=F");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTC USDT", "BTC/USDT", "BTC_USDT", "종목",
        "AAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("허용하지 않는 글자와 스물한 자는 거절한다")
    void 거절하는_표기(String value) {
        assertThatThrownBy(() -> MacroTicker.of(value)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("빈 값은 거절한다")
    void 빈_값() {
        assertThatThrownBy(() -> MacroTicker.of("  ")).isInstanceOf(RuntimeException.class);
    }

    /**
     * <b>이 구분이 왜 생겼는지를 테스트로 남긴다.</b> {@code Symbol.of("^GSPC")} 가 거절하면서
     * 드러났고, 타입이 옳았다.
     */
    @Test
    @DisplayName("같은 표기를 Symbol 은 거절한다 — 그것이 두 타입이 있는 이유다")
    void 심볼과_다르다() {
        assertThat(MacroTicker.of("^GSPC").value()).isEqualTo("^GSPC");
        assertThatThrownBy(() -> Symbol.of("^GSPC")).isInstanceOf(InvalidValueException.class);
    }
}
