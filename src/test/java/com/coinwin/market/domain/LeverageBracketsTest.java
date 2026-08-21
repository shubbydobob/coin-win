package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 레버리지 구간표가 청산가의 유일한 외부 입력이다.
 *
 * <p>이 표가 조용히 틀리면 청산가가 조용히 틀린다. 그래서 <b>표 자체의 내적 정합성</b>을
 * 불러오는 시점에 검사한다. 핵심은 구간 경계에서 유지증거금이 끊기지 않는 것이다.
 *
 * <p>유지증거금은 {@code 명목가 × MMR - 유지증거금 공제액} 이다. 구간 상한 {@code C} 에서
 * 두 구간의 값이 같으려면 {@code a(i+1) = a(i) + C × (r(i+1) - r(i))} 여야 한다. 이 식이
 * 성립하지 않으면 명목가가 1 USDT 늘었을 뿐인데 청산가가 계단처럼 뛴다.
 */
class LeverageBracketsTest {

    private static LeverageBracket bracket(int tier, String cap, String rate, String amount) {
        return new LeverageBracket(tier, Money.of(cap), Percentage.of(rate), Money.of(amount));
    }

    /** 실제 BTCUSDT 구간 1~3. 공제액은 위 연속 조건에서 그대로 나온다. */
    private static LeverageBrackets btcUsdt() {
        return new LeverageBrackets(Symbol.BTC_USDT, List.of(
                bracket(1, "50000", "0.4", "0"),
                bracket(2, "600000", "0.5", "50"),
                bracket(3, "3000000", "1.0", "3050")));
    }

    @Test
    void 명목가가_속한_구간을_찾는다() {
        assertThat(btcUsdt().forNotional(Money.of("10000")).tier()).isEqualTo(1);
        assertThat(btcUsdt().forNotional(Money.of("100000")).tier()).isEqualTo(2);
        assertThat(btcUsdt().forNotional(Money.of("1000000")).tier()).isEqualTo(3);
    }

    /** 상한은 포함이다. 정확히 50,000 인 포지션은 아직 1구간이다. */
    @Test
    void 구간_상한과_같은_명목가는_그_구간에_속한다() {
        assertThat(btcUsdt().forNotional(Money.of("50000")).tier()).isEqualTo(1);
        assertThat(btcUsdt().forNotional(Money.of("50000.01")).tier()).isEqualTo(2);
    }

    /**
     * 마지막 구간을 넘는 명목가는 조용히 마지막 구간으로 떨어뜨리지 않는다.
     * 그런 포지션은 애초에 열 수 없고, 조용히 값을 내면 열 수 있는 것처럼 보인다.
     */
    @Test
    void 마지막_구간을_넘는_명목가는_거부된다() {
        assertThatThrownBy(() -> btcUsdt().forNotional(Money.of("3000000.01")))
                .isInstanceOf(NotionalExceedsBracketsException.class)
                .hasMessageContaining("3000000.01");
    }

    @Test
    void 구간_경계에서_유지증거금이_끊기면_거부한다() {
        // 2구간 공제액이 50 이어야 하는데 40 이면, 명목가 50,000 지점에서 유지증거금이 10 뛴다.
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, List.of(
                bracket(1, "50000", "0.4", "0"),
                bracket(2, "600000", "0.5", "40"))))
                .isInstanceOf(InvalidMarketDataException.class)
                .hasMessageContaining("유지증거금");
    }

    @Test
    void 실제_BTCUSDT_구간표는_연속_조건을_만족한다() {
        assertThat(btcUsdt().brackets()).hasSize(3);
    }

    /** 첫 구간의 공제액은 0 이다. 0 이 아니면 소액 포지션의 유지증거금이 음수가 될 수 있다. */
    @Test
    void 첫_구간의_공제액이_0이_아니면_거부한다() {
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, List.of(
                bracket(1, "50000", "0.4", "10"))))
                .isInstanceOf(InvalidMarketDataException.class)
                .hasMessageContaining("첫 구간");
    }

    @Test
    void 구간_상한이_오름차순이_아니면_거부한다() {
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, List.of(
                bracket(1, "600000", "0.4", "0"),
                bracket(2, "50000", "0.5", "50"))))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    /** MMR 이 명목가에 따라 줄어들면 큰 포지션이 더 안전하다는 뜻이 되어 성립하지 않는다. */
    @Test
    void MMR이_오름차순이_아니면_거부한다() {
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, List.of(
                bracket(1, "50000", "0.5", "0"),
                bracket(2, "600000", "0.4", "-50"))))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void 빈_구간표는_거부된다() {
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, List.of()))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void null은_거부된다() {
        assertThatThrownBy(() -> new LeverageBrackets(null, List.of(bracket(1, "1", "0.4", "0"))))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new LeverageBrackets(Symbol.BTC_USDT, null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> btcUsdt().forNotional(null))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 구간_번호는_1_이상이어야_한다() {
        assertThatThrownBy(() -> bracket(0, "50000", "0.4", "0"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 구간_상한은_0보다_커야_한다() {
        assertThatThrownBy(() -> bracket(1, "0", "0.4", "0"))
                .isInstanceOf(InvalidValueException.class);
    }
}
