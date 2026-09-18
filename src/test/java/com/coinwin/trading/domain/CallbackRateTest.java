package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import org.junit.jupiter.api.Test;

/**
 * 추격 폭. <b>범위를 타입이 막는다.</b>
 *
 * <p>감시 화면이 호가 단수에서 배운 것이 이 타입의 이유다 — 범위가 서비스에 적혀 있으면
 * 인메모리 구현이 거래소가 거절할 값을 만들어도 계약 테스트가 지나간다.
 *
 * <p><b>여기 적힌 경계(0.1 · 10 · 소수 한 자리)는 확인하지 못했다.</b> 이 스위트가 증명하는
 * 것은 "코드가 이 경계를 지킨다" 까지이고, 그 경계가 거래소의 것과 같은지는 테스트넷에
 * 사람이 한 번 붙여 봐야 안다.
 */
class CallbackRateTest {

    @Test
    void 하한보다_작으면_만들_수_없다() {
        assertThatThrownBy(() -> CallbackRate.of("0.05"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("0.1");
    }

    @Test
    void 상한보다_크면_만들_수_없다() {
        assertThatThrownBy(() -> CallbackRate.of("10.1"))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void 경계값은_받아들인다() {
        assertThat(CallbackRate.of("0.1").asPercent().toPlainString()).isEqualTo("0.1");
        assertThat(CallbackRate.of("10").asPercent().toPlainString()).isEqualTo("10.0");
    }

    /** 조용히 반올림하면 <b>건 값과 도는 값이 달라진다.</b> 거부하는 쪽이 옳다. */
    @Test
    void 소수_둘째_자리는_반올림하지_않고_거부한다() {
        assertThatThrownBy(() -> CallbackRate.of("1.25"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("소수 한 자리");
    }

    /** 스케일 4 인 {@code Percentage} 를 그대로 보내면 {@code 1.0000} 이 나간다. */
    @Test
    void 거래소에는_소수_한_자리로_적는다() {
        assertThat(CallbackRate.of("1").asPercent().toPlainString()).isEqualTo("1.0");
    }

    /** 롱은 최고점 <b>아래</b>에서 터진다. 80,000 의 1% 아래인 79,200. */
    @Test
    void 롱은_최고점에서_아래로_그만큼_떨어지면_터진다() {
        assertThat(CallbackRate.of("1").stopFrom(Price.of("80000"), Direction.LONG))
                .isEqualTo(Price.of("79200.00"));
    }

    /** 숏은 최저점 <b>위</b>에서 터진다. 부호를 뒤집으면 이익 구간에서 손절된다. */
    @Test
    void 숏은_최저점에서_위로_그만큼_오르면_터진다() {
        assertThat(CallbackRate.of("1").stopFrom(Price.of("70000"), Direction.SHORT))
                .isEqualTo(Price.of("70700.00"));
    }

    @Test
    void 사람이_읽는_한_조각을_낸다() {
        assertThat(CallbackRate.of("1.5").describe()).isEqualTo("1.5%");
    }
}
