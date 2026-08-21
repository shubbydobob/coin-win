package com.coinwin.market.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleSeries;
import org.junit.jupiter.api.Test;

/**
 * {@link SaveCandlesPort} 의 계약. 저장할 수 있는 두 어댑터(영속화·인메모리)가 통과해야 한다.
 *
 * <p>이 스위트가 Phase 3 완료 조건 "캔들 증분 저장에 중복 없음" 의 증거다. 조건을 문장이 아니라
 * 실행 가능한 형태로 옮기면 이렇게 된다 — <b>같은 것을 다시 저장했을 때 새로 저장되는 수가 0.</b>
 */
public abstract class SaveCandlesPortContract extends LoadCandlesPortContract {

    protected abstract SaveCandlesPort savePort();

    private int save(CandleSeries candles) {
        return savePort().save(SYMBOL, INTERVAL, candles);
    }

    @Test
    void 처음_저장하면_전부_새로_저장된다() {
        assertThat(save(candles(0, 5))).isEqualTo(5);
    }

    @Test
    void 같은_묶음을_다시_저장하면_새로_저장되는_것이_없다() {
        save(candles(0, 5));

        assertThat(save(candles(0, 5))).isZero();
        assertThat(loadPort().load(query(0, 5)).size()).isEqualTo(5);
    }

    /**
     * 실제 증분 수집의 모양이다. 경계가 겹치도록 다시 받아 저장해도 행이 늘지 않아야 한다.
     * 겹쳐 받는 이유는 마지막 캔들이 아직 닫히지 않았을 수 있기 때문이다.
     */
    @Test
    void 겹치는_구간을_이어_저장해도_중복이_생기지_않는다() {
        assertThat(save(candles(0, 5))).isEqualTo(5);
        assertThat(save(candles(3, 8))).isEqualTo(3);

        assertThat(loadPort().load(query(0, 8)).size()).isEqualTo(8);
    }

    @Test
    void 빈_묶음을_저장하면_아무것도_저장되지_않는다() {
        assertThat(save(CandleSeries.empty())).isZero();
        assertThat(loadPort().load(query(0, 5)).isEmpty()).isTrue();
    }

    /**
     * 같은 시각을 다시 받으면 <b>나중에 받은 값이 이긴다.</b> 거래소는 아직 닫히지 않은 캔들을
     * 먼저 주고 나중에 정정해서 다시 주므로, 먼저 받은 값을 지키면 틀린 종가가 영구히 남는다.
     *
     * <p>행 수는 늘지 않고 내용만 바뀐다. 이 둘을 한 테스트에서 함께 단언한다.
     */
    @Test
    void 같은_시각을_다시_저장하면_나중_값이_이기고_행은_늘지_않는다() {
        save(candles(0, 3));

        Candle 정정 = new Candle(hour(1), Price.of("60000"), Price.of("62000"),
                Price.of("59000"), Price.of("61500"), Quantity.of("9.9"));
        assertThat(save(CandleSeries.of(정정))).isZero();

        CandleSeries loaded = loadPort().load(query(0, 3));
        assertThat(loaded.size()).isEqualTo(3);
        assertThat(loaded.candles().get(1).close()).isEqualTo(Price.of("61500"));
        assertThat(loaded.candles().get(1).volume()).isEqualTo(Quantity.of("9.9"));
    }

    /** 저장 어댑터에서 "이미 존재하는 상태" 를 만드는 방법은 그냥 저장하는 것이다. */
    @Override
    protected void givenCandlesExist(CandleSeries candles) {
        save(candles);
    }
}
