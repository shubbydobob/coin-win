package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 캔들 하나가 성립하려면 고가·저가가 나머지 셋을 실제로 감싸야 한다.
 *
 * <p>이 검사가 없으면 잘못된 캔들이 지표 계산까지 조용히 흘러가고, Phase 4 의 일목·볼린저가
 * 트레이딩뷰와 어긋났을 때 원인이 "공식" 인지 "입력" 인지 구분되지 않는다.
 */
class CandleTest {

    private static final Instant OPEN_TIME = Instant.parse("2026-08-01T00:00:00Z");

    private static Candle candle(String open, String high, String low, String close) {
        return new Candle(OPEN_TIME, Price.of(open), Price.of(high),
                Price.of(low), Price.of(close), Quantity.of("12.5"));
    }

    @Test
    void 정상_캔들은_그대로_만들어진다() {
        Candle candle = candle("60000", "61000", "59000", "60500");

        assertThat(candle.openTime()).isEqualTo(OPEN_TIME);
        assertThat(candle.close()).isEqualTo(Price.of("60500"));
        assertThat(candle.volume()).isEqualTo(Quantity.of("12.5"));
    }

    @Test
    void 고가가_저가보다_낮으면_거부된다() {
        assertThatThrownBy(() -> candle("60000", "59000", "61000", "60500"))
                .isInstanceOf(InvalidMarketDataException.class)
                .hasMessageContaining("고가");
    }

    @Test
    void 고가가_시가보다_낮으면_거부된다() {
        assertThatThrownBy(() -> candle("62000", "61000", "59000", "60500"))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void 고가가_종가보다_낮으면_거부된다() {
        assertThatThrownBy(() -> candle("60000", "61000", "59000", "61500"))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void 저가가_시가보다_높으면_거부된다() {
        assertThatThrownBy(() -> candle("58000", "61000", "59000", "60500"))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    @Test
    void 저가가_종가보다_높으면_거부된다() {
        assertThatThrownBy(() -> candle("60000", "61000", "59000", "58500"))
                .isInstanceOf(InvalidMarketDataException.class);
    }

    /** 네 값이 모두 같은 캔들은 실제로 존재한다. 거래가 없던 구간이 그렇다. */
    @Test
    void 시가_고가_저가_종가가_모두_같아도_성립한다() {
        assertThat(candle("60000", "60000", "60000", "60000").high())
                .isEqualTo(Price.of("60000"));
    }

    /** 거래가 없던 구간의 거래량은 0 이다. 0 을 거부하면 실제 데이터를 저장할 수 없다. */
    @Test
    void 거래량_0은_성립한다() {
        Candle candle = new Candle(OPEN_TIME, Price.of("60000"), Price.of("60000"),
                Price.of("60000"), Price.of("60000"), Quantity.of("0"));

        assertThat(candle.volume()).isEqualTo(Quantity.of("0"));
    }

    @Test
    void 시각이_null이면_거부된다() {
        assertThatThrownBy(() -> new Candle(null, Price.of("60000"), Price.of("61000"),
                Price.of("59000"), Price.of("60500"), Quantity.of("1")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 가격이_null이면_거부된다() {
        assertThatThrownBy(() -> new Candle(OPEN_TIME, null, Price.of("61000"),
                Price.of("59000"), Price.of("60500"), Quantity.of("1")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 거래량이_null이면_거부된다() {
        assertThatThrownBy(() -> new Candle(OPEN_TIME, Price.of("60000"), Price.of("61000"),
                Price.of("59000"), Price.of("60500"), null))
                .isInstanceOf(InvalidValueException.class);
    }
}
