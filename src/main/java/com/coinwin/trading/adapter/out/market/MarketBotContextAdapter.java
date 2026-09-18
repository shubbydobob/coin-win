package com.coinwin.trading.adapter.out.market;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.domain.Candle;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import com.coinwin.market.domain.TimeRange;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.domain.AccountState;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.MarketView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
 * <p><b>아직 모자란 자리 — 계좌가 고정값이다.</b> 시작 자산은 설정에서 오고 실현 손익은
 * 언제나 0 이며 열린 포지션도 언제나 비어 있다. 장부 브로커가 체결을 포지션으로 쌓지 않기
 * 때문이다. 기본 전략이 아무것도 하지 않으므로 지금은 드러나지 않지만, <b>전략을 꽂는
 * 순간 이 자리부터 고쳐야 한다</b> — 그 전에 꽂으면 동시 포지션 한계와 손실 한계가 둘 다
 * 헛돈다. {@code docs/spec/trading-bot.md} § 8 에 적어 두었다.
 */
public class MarketBotContextAdapter implements LoadBotContextPort {

    private final LoadOrderBookUseCase tickers;
    private final LoadMarketDataUseCase candles;
    private final TradingProperties properties;

    public MarketBotContextAdapter(
            LoadOrderBookUseCase tickers, LoadMarketDataUseCase candles,
            TradingProperties properties) {
        this.tickers = DomainValues.required(tickers, "시세");
        this.candles = DomainValues.required(candles, "캔들");
        this.properties = DomainValues.required(properties, "설정");
    }

    @Override
    public BotContext contextFor(Symbol symbol) {
        DomainValues.required(symbol, "종목");
        Ticker ticker = tickers.ticker(symbol);
        return new BotContext(
                new MarketView(symbol, ticker.last(), recent(symbol, ticker.at()), ticker.at()),
                AccountState.flat(properties.startingEquity()),
                Optional.empty());
    }

    /**
     * 최근 봉. <b>시각은 시세가 말한 것을 쓴다</b> — 여기서 {@code Instant.now()} 를 부르면
     * 한 사이클 안에서 "지금" 이 둘이 된다.
     */
    private List<Candle> recent(Symbol symbol, Instant at) {
        CandleInterval interval = CandleInterval.ONE_HOUR;
        Instant from = at.minus(interval.length().multipliedBy(properties.candles()));
        return candles.candles(
                new CandleQuery(symbol, interval, new TimeRange(from, at))).candles();
    }
}
