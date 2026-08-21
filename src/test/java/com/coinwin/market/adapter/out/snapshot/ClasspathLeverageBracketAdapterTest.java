package com.coinwin.market.adapter.out.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;
import org.junit.jupiter.api.Test;

/**
 * 커밋된 스냅샷이 <b>실제로 읽히고 정합성 검사를 통과하는지</b>.
 *
 * <p>이 테스트의 값어치는 숫자 몇 개를 확인하는 데 있지 않다. 파일을 손으로 고치다 한 자리를
 * 틀리면 {@link LeverageBrackets} 의 연속성 검사가 여기서 터진다. 그것이 없으면 틀린 구간표가
 * 조용히 틀린 청산가를 만들어 낸다.
 */
class ClasspathLeverageBracketAdapterTest {

    private final ClasspathLeverageBracketAdapter adapter = new ClasspathLeverageBracketAdapter();

    @Test
    void 스냅샷을_읽어_구간표를_만든다() {
        LeverageBrackets brackets = adapter.bracketsFor(Symbol.BTC_USDT);

        assertThat(brackets.symbol()).isEqualTo(Symbol.BTC_USDT);
        assertThat(brackets.brackets()).hasSize(10);
    }

    /** 스냅샷이 도메인으로 넘어가는 순간 연속성·오름차순 검사가 돌았다는 뜻이다. */
    @Test
    void 커밋된_BTCUSDT_구간표는_정합성_검사를_통과한다() {
        assertThat(adapter.bracketsFor(Symbol.BTC_USDT).brackets().getFirst().maintenanceAmount())
                .isEqualTo(Money.of("0"));
    }

    @Test
    void 명목가_5만_이하는_1구간이고_0_4퍼센트다() {
        var 구간 = adapter.bracketsFor(Symbol.BTC_USDT).forNotional(Money.of("30000"));

        assertThat(구간.tier()).isEqualTo(1);
        assertThat(구간.maintenanceMarginRate()).isEqualTo(Percentage.of("0.4"));
        assertThat(구간.maintenanceAmount()).isEqualTo(Money.of("0"));
    }

    @Test
    void 명목가가_5만을_넘으면_2구간이고_공제액_50이_붙는다() {
        var 구간 = adapter.bracketsFor(Symbol.BTC_USDT).forNotional(Money.of("50000.01"));

        assertThat(구간.tier()).isEqualTo(2);
        assertThat(구간.maintenanceMarginRate()).isEqualTo(Percentage.of("0.5"));
        assertThat(구간.maintenanceAmount()).isEqualTo(Money.of("50"));
    }

    @Test
    void 스냅샷이_없는_종목은_외부데이터_예외다() {
        assertThatThrownBy(() -> adapter.bracketsFor(Symbol.of("ETHUSDT")))
                .isInstanceOf(ExternalDataUnavailableException.class)
                .hasMessageContaining("ethusdt");
    }
}
