package com.coinwin.trading.adapter.out.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.in.LoadOutliersUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.MetricHistory;
import com.coinwin.market.domain.MetricKind;
import com.coinwin.market.domain.MetricOutlier;
import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.PriceLevel;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.domain.BotContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 봇이 시장에서 무엇을 읽어 오는가.
 *
 * <p><b>이 스위트가 지키는 문장은 하나다:</b> 읽기가 실패해도 봇은 멈추지 않고, <b>못 읽었다는
 * 사실이 그대로 전달된다.</b> 빈 값을 "특별한 일 없음" 으로 채우면 침묵 위에서 주문이 나간다 —
 * {@code account} 가 폴백을 두지 않은 것, 환율을 못 얻으면 원화가 통째로 비는 것과 같은 규칙이다.
 *
 * <p>거래소를 때리지 않는다. 세 유스케이스를 손으로 만든 가짜로 바꾼다.
 */
class MarketBotContextAdapterTest {

    private static final Symbol SYMBOL = Symbol.BTC_USDT;
    private static final Instant AT = Instant.parse("2026-09-19T00:00:00Z");

    /** 일목 워밍업(52+26)과 200 이동평균을 넘기려면 봉이 넉넉해야 한다. */
    private static final int ENOUGH = 300;

    @Test
    void 캔들이_충분하면_지표와_대와_매물대를_읽는다() {
        BotContext context = adapter(ENOUGH, Fail.NOTHING).contextFor(SYMBOL);

        assertThat(context.reading().readout()).isPresent();
        assertThat(context.reading().complete()).isTrue();
    }

    /**
     * <b>봉이 모자란 것은 고장이 아니라 아직 말할 수 없는 상태다.</b> 워밍업이 안 찬 지표를
     * 억지로 채우면 그 값이 판단에 들어간다.
     */
    @Test
    void 캔들이_모자라면_판독만_비고_나머지는_산다() {
        BotContext context = adapter(10, Fail.NOTHING).contextFor(SYMBOL);

        assertThat(context.reading().readout()).isEmpty();
        assertThat(context.reading().book()).isPresent();
        assertThat(context.reading().outliers()).isPresent();
    }

    /** 호가를 못 읽었다고 지표까지 버리면 읽은 것을 버리는 것이다. */
    @Test
    void 호가를_못_읽어도_지표는_남는다() {
        BotContext context = adapter(ENOUGH, Fail.BOOK).contextFor(SYMBOL);

        assertThat(context.reading().book()).isEmpty();
        assertThat(context.reading().readout()).isPresent();
    }

    @Test
    void 이상치를_못_읽어도_나머지는_남는다() {
        BotContext context = adapter(ENOUGH, Fail.OUTLIERS).contextFor(SYMBOL);

        assertThat(context.reading().outliers()).isEmpty();
        assertThat(context.reading().readout()).isPresent();
        assertThat(context.reading().book()).isPresent();
    }

    /**
     * <b>전부 못 읽어도 던지지 않는다.</b> 사람이 안 보는 동안 도는 루프가 읽기 실패로 서면
     * 그 뒤의 사이클이 전부 사라진다 — 손절을 다시 거는 일(R2)까지 함께 멈춘다.
     */
    @Test
    void 전부_못_읽어도_컨텍스트는_나오고_비어_있다는_사실이_남는다() {
        BotContext context = adapter(10, Fail.BOTH).contextFor(SYMBOL);

        assertThat(context.reading().complete()).isFalse();
        assertThat(context.view().mark()).isEqualTo(Price.of("78000"));
    }

    private enum Fail { NOTHING, BOOK, OUTLIERS, BOTH }

    private static MarketBotContextAdapter adapter(int bars, Fail fail) {
        FakeCandles candles = new FakeCandles(bars);
        return new MarketBotContextAdapter(
                new MarketAccess(new FakeBook(fail), candles, candles, new FakeOutliers(fail)),
                new TradingProperties(true, null, null, bars, null, null, null,
                        "hold", null, null, null, null));
    }

    private record FakeBook(Fail fail) implements LoadOrderBookUseCase {

        @Override
        public OrderBook orderBook(Symbol symbol, OrderBookDepth depth) {
            if (fail == Fail.BOOK || fail == Fail.BOTH) {
                throw new ExternalDataUnavailableException("호가를 못 읽었다");
            }
            return new OrderBook(symbol,
                    List.of(new PriceLevel(Price.of("77990"), Quantity.of("1"))),
                    List.of(new PriceLevel(Price.of("78010"), Quantity.of("1"))), AT);
        }

        @Override
        public Ticker ticker(Symbol symbol) {
            return new Ticker(symbol, Price.of("78000"), BigDecimal.ZERO,
                    Price.of("79000"), Price.of("77000"), Quantity.of("100"), AT);
        }
    }

    /**
     * 단조 증가 캔들. 값이 무엇인지는 상관없고 <b>몇 개인지</b>만 본다.
     *
     * <p><b>채우는 쪽도 겸한다.</b> 진짜 경로가 채운 뒤에 읽으므로 가짜도 그 둘을 다 가져야
     * 하고, 여기서 채움이 아무 일도 안 하는 것은 <b>이미 다 있다</b>는 뜻이다.
     */
    private record FakeCandles(int bars) implements LoadMarketDataUseCase, SyncMarketDataUseCase {

        @Override
        public int sync(CandleQuery query) {
            return 0;
        }

        @Override
        public CandleSeries candles(CandleQuery query) {
            List<Candle> made = new ArrayList<>();
            for (int i = 0; i < bars; i++) {
                Price open = Price.of(String.valueOf(70000 + i * 10));
                Price close = Price.of(String.valueOf(70005 + i * 10));
                made.add(new Candle(AT.minus(Duration.ofHours(bars - (long) i)),
                        open, close, open, close, Quantity.of("1")));
            }
            return new CandleSeries(made);
        }
    }

    private record FakeOutliers(Fail fail) implements LoadOutliersUseCase {

        @Override
        public MarketOutliers outliers(Symbol symbol) {
            if (fail == Fail.OUTLIERS || fail == Fail.BOTH) {
                throw new ExternalDataUnavailableException("이상치를 못 읽었다");
            }
            return MarketOutliers.of(symbol, AT, List.of(), priceOutlier());
        }

        /** 가격 이상치는 비어 있을 수 없다. 표본 셋이면 분위가 나온다. */
        private static MetricOutlier priceOutlier() {
            return MetricOutlier.of(MetricKind.PRICE, new BigDecimal("78000"),
                    new MetricHistory(List.of(
                            new BigDecimal("77000"),
                            new BigDecimal("78000"),
                            new BigDecimal("79000"))));
        }
    }
}
