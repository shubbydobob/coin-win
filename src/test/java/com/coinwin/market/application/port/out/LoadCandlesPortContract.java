package com.coinwin.market.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * {@link LoadCandlesPort} 의 계약. <b>세 어댑터가 모두 이 스위트를 통과해야 한다.</b>
 *
 * <p>어댑터마다 테스트를 따로 쓰면 각자 자기 구현이 하는 일을 검사하게 되고, 포트가 실제로
 * 하나의 약속인지는 아무도 확인하지 않는다. Phase 6 의 백테스트는 같은 포트로 과거 데이터와
 * 실시간을 갈아 끼우는데, 그때 갈아 끼울 수 있다는 근거가 이 스위트뿐이다.
 *
 * <p>근거: {@code .claude/docs/testing.md} — "하나의 테스트 스위트를 모든 어댑터 구현체에
 * 대해 실행한다."
 */
public abstract class LoadCandlesPortContract {

    protected static final Symbol SYMBOL = Symbol.BTC_USDT;
    protected static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;
    protected static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    /** 검사 대상 어댑터. */
    protected abstract LoadCandlesPort loadPort();

    /**
     * 어댑터가 이 캔들들을 이미 가지고 있는 상태로 만든다.
     *
     * <p>"저장" 이 아니라 "존재" 라고 부르는 이유는 거래소 어댑터가 저장할 수 없기 때문이다.
     * 그쪽 구현은 페이크 거래소가 이 캔들들을 돌려주도록 준비한다.
     */
    protected abstract void givenCandlesExist(CandleSeries candles);

    protected static Instant hour(int index) {
        return T0.plus(INTERVAL.length().multipliedBy(index));
    }

    /** 시각마다 종가가 달라 어느 캔들이 돌아왔는지 구분할 수 있게 한다. */
    protected static Candle candleAt(int index) {
        Price close = Price.of(String.valueOf(60000 + index));
        return new Candle(hour(index), Price.of("60000"), Price.of("61000"),
                Price.of("59000"), close, Quantity.of("1.5"));
    }

    protected static CandleSeries candles(int fromIndex, int toIndexExclusive) {
        return new CandleSeries(IntStream.range(fromIndex, toIndexExclusive)
                .mapToObj(LoadCandlesPortContract::candleAt)
                .toList());
    }

    protected static CandleQuery query(int fromIndex, int toIndexExclusive) {
        return new CandleQuery(SYMBOL, INTERVAL,
                new TimeRange(hour(fromIndex), hour(toIndexExclusive)));
    }

    @Test
    void 조회_구간에_든_캔들을_돌려준다() {
        givenCandlesExist(candles(0, 5));

        CandleSeries loaded = loadPort().load(query(0, 5));

        assertThat(loaded.size()).isEqualTo(5);
        assertThat(loaded.first().openTime()).isEqualTo(hour(0));
        assertThat(loaded.last().openTime()).isEqualTo(hour(4));
    }

    @Test
    void 구간_앞뒤의_캔들은_돌려주지_않는다() {
        givenCandlesExist(candles(0, 10));

        CandleSeries loaded = loadPort().load(query(3, 6));

        assertThat(loaded.candles()).extracting(Candle::openTime)
                .containsExactly(hour(3), hour(4), hour(5));
    }

    /** 구간은 반열림이다. 끝 시각의 캔들이 딸려 오면 연속 조회에서 곧바로 중복이 된다. */
    @Test
    void 끝_시각의_캔들은_포함하지_않는다() {
        givenCandlesExist(candles(0, 5));

        assertThat(loadPort().load(query(0, 3)).candles())
                .extracting(Candle::openTime)
                .containsExactly(hour(0), hour(1), hour(2));
    }

    /** 연속한 두 구간을 이어 받아도 같은 시각이 양쪽에 들지 않는다. */
    @Test
    void 경계를_맞댄_두_구간의_결과는_겹치지_않는다() {
        givenCandlesExist(candles(0, 6));

        CandleSeries 앞 = loadPort().load(query(0, 3));
        CandleSeries 뒤 = loadPort().load(query(3, 6));

        assertThat(앞.merge(뒤).size()).isEqualTo(앞.size() + 뒤.size());
    }

    @Test
    void 데이터가_없으면_빈_묶음을_돌려준다() {
        assertThat(loadPort().load(query(0, 3)).isEmpty()).isTrue();
    }

    @Test
    void 구간에_걸치는_캔들이_하나도_없으면_빈_묶음을_돌려준다() {
        givenCandlesExist(candles(0, 3));

        assertThat(loadPort().load(query(10, 13)).isEmpty()).isTrue();
    }

    /** 같은 질의는 같은 답을 낸다. 백테스트의 재현성이 여기서 시작된다. */
    @Test
    void 같은_구간을_두_번_조회하면_같은_결과다() {
        givenCandlesExist(candles(0, 5));

        assertThat(loadPort().load(query(0, 5))).isEqualTo(loadPort().load(query(0, 5)));
    }

    /**
     * 반환 순서는 어댑터의 사정이 아니라 계약이다. {@link CandleSeries} 가 시간 오름차순을
     * 강제하므로, 어댑터가 순서를 흐트러뜨리면 묶음을 만드는 순간 예외가 난다.
     */
    @Test
    void 시간_오름차순으로_돌려준다() {
        givenCandlesExist(candles(0, 5));

        List<Instant> openTimes = loadPort().load(query(0, 5)).candles().stream()
                .map(Candle::openTime).toList();

        assertThat(openTimes).isSorted();
    }
}
