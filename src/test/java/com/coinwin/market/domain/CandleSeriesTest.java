package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 캔들 묶음의 불변식이 이 Phase 의 완료 조건("캔들 증분 저장에 중복 없음")을 타입 수준에서
 * 떠받친다.
 *
 * <p>중복 제거를 어댑터 세 곳에 각각 심으면 세 곳의 정의가 갈라진다. 여기서 한 번 막으면
 * 어느 어댑터가 중복을 흘리든 묶음을 만드는 순간 터진다.
 */
class CandleSeriesTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-01T02:00:00Z");

    static Candle candle(Instant openTime, String close) {
        return new Candle(openTime, Price.of("60000"), Price.of("61000"),
                Price.of("59000"), Price.of(close), Quantity.of("12.5"));
    }

    @Test
    void 같은_시각의_캔들이_두_번_들어오면_거부한다() {
        assertThatThrownBy(() -> new CandleSeries(List.of(candle(T0, "60500"), candle(T0, "60600"))))
                .isInstanceOf(DuplicateCandleException.class)
                .hasMessageContaining(T0.toString());
    }

    @Test
    void 시간_역순으로_들어오면_거부한다() {
        assertThatThrownBy(() -> new CandleSeries(List.of(candle(T1, "60500"), candle(T0, "60600"))))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void null_목록은_거부된다() {
        assertThatThrownBy(() -> new CandleSeries(null)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 빈_묶음은_성립한다() {
        assertThat(CandleSeries.empty().isEmpty()).isTrue();
        assertThat(CandleSeries.empty().size()).isZero();
    }

    @Test
    void 원본_목록을_나중에_바꿔도_묶음은_변하지_않는다() {
        List<Candle> mutable = new ArrayList<>(List.of(candle(T0, "60500")));
        CandleSeries series = new CandleSeries(mutable);

        mutable.add(candle(T1, "60600"));

        assertThat(series.size()).isEqualTo(1);
    }

    @Test
    void 반환된_목록은_수정할_수_없다() {
        CandleSeries series = CandleSeries.of(candle(T0, "60500"));

        assertThatThrownBy(() -> series.candles().add(candle(T1, "60600")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 첫_캔들과_마지막_캔들을_알려준다() {
        CandleSeries series = CandleSeries.of(candle(T0, "60100"), candle(T1, "60200"), candle(T2, "60300"));

        assertThat(series.first().close()).isEqualTo(Price.of("60100"));
        assertThat(series.last().close()).isEqualTo(Price.of("60300"));
    }

    @Test
    void 빈_묶음에는_첫_캔들도_마지막_캔들도_없다() {
        assertThatThrownBy(() -> CandleSeries.empty().first())
                .isInstanceOf(InvalidMarketDataException.class);
        assertThatThrownBy(() -> CandleSeries.empty().last())
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void 구간으로_자르면_반열림_구간에_든_캔들만_남는다() {
        CandleSeries series = CandleSeries.of(candle(T0, "60100"), candle(T1, "60200"), candle(T2, "60300"));

        CandleSeries 잘린것 = series.within(new TimeRange(T1, T2));

        assertThat(잘린것.size()).isEqualTo(1);
        assertThat(잘린것.first().openTime()).isEqualTo(T1);
    }

    /**
     * 증분 저장의 도메인 표현. 겹치는 구간을 다시 받아도 개수가 늘지 않아야 한다 —
     * 이것이 세 어댑터 모두에게 요구되는 성질이다.
     */
    @Test
    void 겹치는_묶음을_합쳐도_같은_시각이_두_번_남지_않는다() {
        CandleSeries 먼저 = CandleSeries.of(candle(T0, "60100"), candle(T1, "60200"));
        CandleSeries 나중 = CandleSeries.of(candle(T1, "60900"), candle(T2, "60300"));

        CandleSeries 합친것 = 먼저.merge(나중);

        assertThat(합친것.size()).isEqualTo(3);
        assertThat(합친것.candles()).extracting(Candle::openTime).containsExactly(T0, T1, T2);
    }

    /**
     * 겹칠 때 어느 쪽이 이기는지는 정해져 있어야 한다. 나중에 받은 값이 이긴다 —
     * 거래소가 미확정 캔들을 나중에 정정해 보내기 때문이다.
     */
    @Test
    void 겹치는_시각은_나중에_받은_값이_이긴다() {
        CandleSeries 먼저 = CandleSeries.of(candle(T1, "60200"));
        CandleSeries 나중 = CandleSeries.of(candle(T1, "60900"));

        assertThat(먼저.merge(나중).first().close()).isEqualTo(Price.of("60900"));
    }

    @Test
    void 빈_묶음과_합치면_원본_그대로다() {
        CandleSeries 원본 = CandleSeries.of(candle(T0, "60100"));

        assertThat(원본.merge(CandleSeries.empty())).isEqualTo(원본);
        assertThat(CandleSeries.empty().merge(원본)).isEqualTo(원본);
    }
}
