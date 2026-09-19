package com.coinwin.trading.adapter.out.market;

import com.coinwin.backtest.domain.ZoneSettings;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.OrderBookDepth;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.readout.domain.TimeframeReadout;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.domain.AccountState;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.MarketReading;
import com.coinwin.trading.domain.MarketView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 시장은 거래소에서, 계좌는 장부에서 읽어 한 뭉치로 만든다.
 *
 * <p><b>{@code market} 의 인바운드 포트를 소비한다.</b> 아웃바운드가 아니라 인바운드인 이유는
 * "캔들을 어디서 얻는가" 가 {@code market} 의 정책이기 때문이다 —
 * {@code projection.api → market.application.port.in} 과 같은 판단이다.
 *
 * <p><b>조립이 어댑터에 있는 것이 요점이다.</b> {@code trading.application} 은 다른 모듈을
 * 하나도 모르고, 알아야 할 것은 {@code BotContext} 뿐이다.
 *
 * <p><b>판독을 여기서 만든다.</b> 화면이 쓰는 {@link TimeframeReadout} 을 그대로 쓰므로
 * 봇과 사람이 <b>같은 지표 값을 본다</b> — 파이썬이 아니라 자바가 지표를 계산하게 해 둔 것과
 * 같은 이유이고, 정의가 두 곳이 되면 화면이 보여 주는 값과 봇이 판단한 값이 갈라진다.
 *
 * <p><b>셋(판독 · 호가 · 이상치)을 따로 읽고 따로 흡수한다.</b> 호가를 못 읽었다고 지표까지
 * 버리면 읽은 것을 버리는 것이다. 그리고 실패를 <b>로그에 남긴다</b> — 봇을 멈출 이유는
 * 아니지만 조용히 사라지면 "특별한 일 없음" 과 구별되지 않고, 이 저장소는 원인을 못 봐서
 * 막힌 일을 이미 두 번 겪었다.
 *
 * <p><b>봉을 채운 뒤에 읽는다.</b> {@code LoadMarketDataUseCase} 는 저장된 것만 주고 거래소를
 * 때리지 않는다 — 처음에는 그것만 불렀고, 저장된 1시간봉이 없는 기계에서 <b>봉을 0개 받고
 * 있었다.</b> 아무도 몰랐던 이유는 그때 캔들을 쓰는 전략이 없었기 때문이다. 판독이 붙으면서
 * 드러났다.
 *
 * <p>같은 실수를 {@code ReadoutService} 가 먼저 겪었고 그 자리에 이유가 적혀 있다 —
 * <b>포트 문서가 "모자라면 채워서 준다" 고 적고 있었는데 구현은 그렇지 않았다.</b> 저장은 같은
 * 봉을 다시 넣어도 안전하므로(저장소가 기본키로 거른다) 매번 같은 구간을 채워도 새로 들어가는
 * 것은 그 사이 생긴 봉뿐이다.
 *
 * <p><b>계좌는 이 어댑터의 일이 아니다.</b> 여기서 내는 것은 시작 자산과 빈 포지션이고,
 * 실제 계좌는 감싸는 쪽이 얹는다({@code PaperBotContextAdapter}). 시장을 읽는 코드를 두 모드가
 * 나눠 갖게 하려는 것이고, 그래야 장부와 실계좌가 <b>같은 값을 본다.</b>
 */
public class MarketBotContextAdapter implements LoadBotContextPort {

    private static final Logger LOG = LoggerFactory.getLogger(MarketBotContextAdapter.class);

    /** 판독이 보는 주기. <b>봇의 주기가 아니라 봉의 주기다</b> — 1분마다 깨어나도 4시간봉을 본다. */
    private static final CandleInterval INTERVAL = CandleInterval.ONE_HOUR;

    private final MarketAccess market;
    private final TradingProperties properties;

    public MarketBotContextAdapter(MarketAccess market, TradingProperties properties) {
        this.market = DomainValues.required(market, "시장 접근");
        this.properties = DomainValues.required(properties, "설정");
    }

    @Override
    public BotContext contextFor(Symbol symbol) {
        DomainValues.required(symbol, "종목");
        Ticker ticker = market.tickers().ticker(symbol);
        List<Candle> recent = recent(symbol, ticker.at());
        return new BotContext(
                new MarketView(symbol, ticker.last(), recent, ticker.at()),
                readingOf(symbol, recent),
                AccountState.flat(properties.startingEquity()),
                Optional.empty(), List.of());
    }

    /**
     * 시장을 읽는다. <b>하나가 실패해도 나머지는 산다.</b>
     *
     * <p>판독은 봉이 모자라면 던진다(일목 워밍업이 가장 길다). 그것은 고장이 아니라 아직
     * 말할 수 없는 상태이고, 빈 값이 그 사실을 그대로 나른다.
     */
    private MarketReading readingOf(Symbol symbol, List<Candle> recent) {
        return new MarketReading(
                attempt("판독", () -> TimeframeReadout.over(INTERVAL, new CandleSeries(recent),
                        ZoneSettings.standard(), VolumeProfileSettings.standard())),
                attempt("호가", () -> market.tickers().orderBook(symbol, OrderBookDepth.of(20))),
                attempt("이상치", () -> market.outliers().outliers(symbol)));
    }

    /**
     * 읽어 보고 실패하면 비운다. <b>원인은 남긴다.</b>
     *
     * <p>{@link RuntimeException} 을 통째로 잡는 것은 <b>봇이 읽기 실패로 죽으면 안 되기</b>
     * 때문이다. 좁게 잡으면 새 예외가 생길 때마다 그 자리를 다시 고쳐야 하고, 빠뜨린 한 번이
     * 루프를 세운다 — 사람이 안 보는 동안 도는 루프에서 그 대가가 크다. 대신 <b>버리지 않고
     * 찍는다.</b>
     */
    private static <T> Optional<T> attempt(String what, Supplier<T> read) {
        try {
            return Optional.of(read.get());
        } catch (RuntimeException e) {
            LOG.warn("읽지 못했다: {} — 그 자리는 비운다", what, e);
            return Optional.empty();
        }
    }

    /**
     * 최근 봉. <b>시각은 시세가 말한 것을 쓴다</b> — 여기서 {@code Instant.now()} 를 부르면
     * 한 사이클 안에서 "지금" 이 둘이 된다.
     */
    private List<Candle> recent(Symbol symbol, Instant at) {
        Instant from = at.minus(INTERVAL.length().multipliedBy(properties.candles()));
        CandleQuery query = new CandleQuery(symbol, INTERVAL, new TimeRange(from, at));
        market.sync().sync(query);
        return market.candles().candles(query).candles();
    }
}
