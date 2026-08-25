package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 호가가 두 이름으로 온다 — REST 는 {@code bids}/{@code asks}, 웹소켓은 {@code b}/{@code a}.
 *
 * <p><b>아래 두 JSON 은 실제로 받은 것이다</b>(2026-08-25). 손으로 지어낸 모양으로 매핑을
 * 고정하면 그 테스트는 우리 상상만 증명한다 — 호가 단수 검사를 "1 과 20 사이" 로 상상했다가
 * {@code -4021} 로 거절당한 것이 같은 종류의 실수였다.
 */
class BinanceDepthTest {

    private static final Symbol BTC = Symbol.of("BTCUSDT");

    private static final Instant OUR_CLOCK = Instant.parse("2026-08-25T00:00:00Z");

    private final JsonMapper json = new JsonMapper();

    @Test
    void REST_응답을_호가로_옮긴다() {
        OrderBook book = read("""
                {"lastUpdateId":11377001557469,
                 "E":1787592539515,"T":1787592539514,
                 "bids":[["78562.70","0.775"],["78562.60","5.128"]],
                 "asks":[["78562.80","1.848"],["78563.00","0.002"]]}""")
                .toOrderBook(BTC, OUR_CLOCK);

        assertThat(book.bestBid().value()).isEqualByComparingTo("78562.70");
        assertThat(book.bestAsk().value()).isEqualByComparingTo("78562.80");
        assertThat(book.at()).isEqualTo(Instant.ofEpochMilli(1787592539515L));
    }

    @Test
    void 웹소켓_페이로드를_같은_호가로_옮긴다() {
        BinanceDepth depth = read("""
                {"e":"depthUpdate","E":1787592539515,"T":1787592539514,
                 "s":"BTCUSDT","ps":"BTCUSDT",
                 "U":11377001539649,"u":11377001557469,"pu":11377001539214,
                 "b":[["78562.70","0.775"],["78562.60","5.128"]],
                 "a":[["78562.80","1.848"],["78563.00","0.002"]],"st":1}""");

        OrderBook book = depth.toOrderBook(depth.streamedSymbol(), OUR_CLOCK);

        assertThat(depth.streamedSymbol()).isEqualTo(BTC);
        assertThat(book.bestBid().value()).isEqualByComparingTo("78562.70");
        assertThat(book.bestAsk().value()).isEqualByComparingTo("78562.80");
        assertThat(book.bidVolume().value()).isEqualByComparingTo("5.903");
    }

    /** 거래소 시각이 없을 때만 우리 시계를 쓴다. 우리 시계를 거래소 시각인 척 두지 않는다. */
    @Test
    void 거래소가_시각을_주지_않으면_우리_시계로_적는다() {
        OrderBook book = read("""
                {"bids":[["78562.70","0.775"]],"asks":[["78562.80","1.848"]]}""")
                .toOrderBook(BTC, OUR_CLOCK);

        assertThat(book.at()).isEqualTo(OUR_CLOCK);
    }

    @Test
    void 종목이_없는_스트림_메시지는_버린다() {
        BinanceDepth depth = read("""
                {"e":"depthUpdate","E":1787592539515,
                 "b":[["78562.70","0.775"]],"a":[["78562.80","1.848"]]}""");

        assertThatThrownBy(depth::streamedSymbol)
                .isInstanceOf(BinanceResponseException.class);
    }

    @Test
    void 호가가_비어_있으면_실패한다() {
        assertThatThrownBy(() -> read("{\"E\":1787592539515}").toOrderBook(BTC, OUR_CLOCK))
                .isInstanceOf(BinanceResponseException.class);
    }

    private BinanceDepth read(String body) {
        return json.readValue(body, BinanceDepth.class);
    }
}
