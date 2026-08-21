package com.coinwin.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.BacktestFixtures;
import com.coinwin.common.domain.Price;
import com.coinwin.market.domain.Candle;
import com.coinwin.position.domain.Direction;
import org.junit.jupiter.api.Test;

class TriggerTest {

    private static final Price TARGET = Price.of("100");

    private static Trigger falling() {
        return new Trigger(TARGET, Trigger.Approach.FALLING);
    }

    private static Trigger rising() {
        return new Trigger(TARGET, Trigger.Approach.RISING);
    }

    /** 시가/고가/저가/종가. */
    private static Candle bar(String ohlc) {
        return BacktestFixtures.ohlc(0, ohlc);
    }

    @Test
    void 내려와_닿으면_트리거_가격에_체결된다() {
        assertThat(falling().fillIn(bar("110/112/99/105"))).contains(TARGET);
    }

    @Test
    void 닿지_않으면_체결되지_않는다() {
        assertThat(falling().fillIn(bar("110/112/101/105"))).isEmpty();
        assertThat(rising().fillIn(bar("90/99/88/95"))).isEmpty();
    }

    /** 경계에 정확히 닿은 것도 체결이다. 지정가는 그 가격에 걸려 있다. */
    @Test
    void 저가가_트리거와_정확히_같으면_체결된다() {
        assertThat(falling().fillIn(bar("110/112/100/105"))).contains(TARGET);
        assertThat(rising().fillIn(bar("90/100/88/95"))).contains(TARGET);
    }

    /**
     * 갭. 트리거를 이미 지나쳐서 열린 봉은 <b>시가</b>에 체결된다.
     *
     * <p>손절가 100 을 뛰어넘어 95 에 열렸으면 실제 체결은 95 다. 100 에 체결됐다고 보면
     * 백테스트가 실제보다 좋게 나온다.
     */
    @Test
    void 트리거를_지나쳐_열린_봉은_시가에_체결된다() {
        assertThat(falling().fillIn(bar("95/97/90/96"))).contains(Price.of("95"));
        assertThat(rising().fillIn(bar("105/108/104/107"))).contains(Price.of("105"));
    }

    /** 시가가 트리거와 같으면 갭이 아니다. 어느 쪽으로 체결해도 같은 값이라 구분이 필요 없다. */
    @Test
    void 시가가_트리거와_같으면_트리거_가격에_체결된다() {
        assertThat(falling().fillIn(bar("100/105/98/102"))).contains(TARGET);
    }

    @Test
    void 롱은_하락해서_숏은_상승해서_닿는_것이_진입과_손절이다() {
        assertThat(Trigger.adverse(Direction.LONG, TARGET).approach())
                .isEqualTo(Trigger.Approach.FALLING);
        assertThat(Trigger.adverse(Direction.SHORT, TARGET).approach())
                .isEqualTo(Trigger.Approach.RISING);
    }

    @Test
    void 익절은_그_반대다() {
        assertThat(Trigger.benign(Direction.LONG, TARGET).approach())
                .isEqualTo(Trigger.Approach.RISING);
        assertThat(Trigger.benign(Direction.SHORT, TARGET).approach())
                .isEqualTo(Trigger.Approach.FALLING);
    }
}
