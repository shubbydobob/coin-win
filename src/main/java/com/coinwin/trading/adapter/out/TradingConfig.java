package com.coinwin.trading.adapter.out;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.trading.adapter.out.market.MarketBotContextAdapter;
import com.coinwin.common.binance.BinanceServerClock;
import com.coinwin.trading.adapter.out.binance.BinanceOrderAdapter;
import com.coinwin.trading.adapter.out.binance.BinanceOrderCredentials;
import com.coinwin.trading.adapter.out.paper.PaperBotContextAdapter;
import com.coinwin.trading.adapter.out.paper.PaperBrokerAdapter;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.domain.HoldStrategy;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.StopLossGuardStrategy;
import com.coinwin.trading.domain.TradingMode;
import com.coinwin.trading.domain.TradingStrategy;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * 봇의 조립.
 *
 * <p><b>{@code config} 가 아니라 {@code adapter.out} 아래 있다.</b> 규칙 2 는 네 층
 * (api · adapter · application · domain)만 알고, 어느 층에도 속하지 않는 패키지는 도메인도
 * 응용도 참조할 수 없다 — Phase 3 에서 {@code adapter.out} 이 같은 함정에 빠졌던 자리다.
 * {@code market} · {@code account} · {@code watch} 의 설정이 전부 어댑터 아래 있는 것도
 * 같은 이유이고, 조립은 실제로 바깥 층의 일이므로 규칙이 옳다.
 *
 * <p><b>브로커는 장부 하나뿐이다.</b> 테스트넷·실계좌 어댑터는 아직 없고, 그래서 지금 이
 * 앱으로는 돈이 움직일 수 없다. 그것들이 생길 때 <b>이 자리에서 모드를 보고 고르되,
 * {@link TradingMode#LIVE} 를 요구했는데 못 만들면 던진다</b> — 조용히 장부로 내려오면
 * "실계좌인 줄 알았는데 장부였다" 가 되고, 그 반대는 더 나쁘다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({TradingProperties.class, BinanceOrderCredentials.class})
public class TradingConfig {

    /**
     * 기본 전략은 아무것도 하지 않는 것이다.
     *
     * <p>임시 구현이 아니라 옳은 기본값이다 — 이 저장소가 두 번 쟀고 두 번 다 진입 규칙이
     * 없다고 나왔다({@code docs/adr/021} · {@code docs/adr/022}).
     *
     * <p>{@code stop-guard} 로 바꾸면 <b>진입은 여전히 안 하고 손절만 지킨다.</b> 그 규칙은
     * 엣지를 요구하지 않으므로 진입 규칙이 없는 지금도 꽂을 수 있다 —
     * {@link StopLossGuardStrategy} 에 근거가 적혀 있다.
     */
    @Bean
    TradingStrategy tradingStrategy(TradingProperties properties) {
        return "stop-guard".equalsIgnoreCase(properties.strategy())
                ? new StopLossGuardStrategy(properties.stopDistance())
                : new HoldStrategy();
    }

    /** 자리표시자 한계. 근거 있는 수가 아니라는 것이 {@code RiskLimits} 에 적혀 있다. */
    @Bean
    RiskLimits riskLimits() {
        return RiskLimits.placeholder();
    }

    /**
     * 장부 브로커. 모드와 무관하게 언제나 만든다 — 장부 모드가 아니어도 이 객체가 컨텍스트를
     * 조립하는 자리에 있고, 만드는 데 키가 필요 없다.
     *
     * <p>포트 타입으로 빈을 하나 더 두지 않는다. 같은 객체가 두 이름으로 올라가면 주입할 때
     * <b>어느 것이 진짜인지 스프링이 묻는다</b>(실제로 컨텍스트가 그 자리에서 안 떴다).
     */
    @Bean
    PaperBrokerAdapter paperBrokerAdapter(TradingProperties properties) {
        return new PaperBrokerAdapter(
                costsFrom(properties), Clock.systemUTC(), properties.startingEquity());
    }

    /**
     * 주문이 어디로 가는가. <b>모드가 이 한 줄에서 갈린다.</b>
     *
     * <p><b>폴백이 없다.</b> {@code TESTNET} 이나 {@code LIVE} 를 요구했는데 키가 없으면
     * 앱이 그 자리에서 뜨지 않는다 — 조용히 장부로 내려오면 "실계좌인 줄 알았는데 장부였다"
     * 가 되고, 그 반대는 더 나쁘다. {@code account} 가 키 없을 때 인메모리로 대신 올리지
     * 않기로 한 것과 같은 규칙이다.
     *
     * <p><b>{@code @Primary} 인 이유</b>는 장부 브로커도 이 타입이기 때문이다. 표시가 없으면
     * 스프링이 어느 쪽인지 묻고, 그 답이 "감싸지 않은 장부" 로 정해지면 <b>실계좌 모드로
     * 띄웠는데 장부에 적히는</b> 상태가 된다 — 이 설정에서 가장 나쁜 고장이다.
     */
    @Bean
    @Primary
    PlaceOrderPort placeOrderPort(
            TradingProperties properties, PaperBrokerAdapter paperBrokerAdapter,
            BinanceOrderCredentials credentials, BinanceServerClock binanceServerClock) {
        if (properties.mode() == TradingMode.PAPER) {
            return paperBrokerAdapter;
        }
        if (!credentials.isComplete()) {
            throw new IllegalStateException(
                    "%s 모드인데 주문 키가 없다. COINWIN_TRADING_BINANCE_API_KEY 와 "
                            .formatted(properties.mode())
                            + "COINWIN_TRADING_BINANCE_SECRET_KEY 가 필요하다 — "
                            + "장부로 대신 내려오지 않는다");
        }
        return new BinanceOrderAdapter(
                exchangeClient(properties), credentials, binanceServerClock, properties.mode());
    }

    /** 테스트넷과 실계좌는 주소만 다르다. 그 사실이 위험해서 모드와 함께 다닌다. */
    private static RestClient exchangeClient(TradingProperties properties) {
        return RestClient.builder().baseUrl(properties.exchangeUrl()).build();
    }

    /**
     * 봇이 보는 세상. <b>장부 쪽이 시장 어댑터를 감싼다</b> — 두 모드가 시장을 읽는 코드를
     * 나눠 갖게 하려는 것이고, 그래야 장부와 실계좌가 같은 값을 본다.
     *
     * <p>시장 어댑터를 빈으로 두지 않고 여기서 만든다. 둘 다 {@link LoadBotContextPort} 라
     * 빈이 둘이면 주입할 때 <b>어느 것이 진짜인지 스프링이 묻는다</b> — 그리고 그 답이
     * "감싸지 않은 쪽" 으로 정해지면 봇이 계좌를 못 보는 채로 조용히 돈다.
     */
    @Bean
    LoadBotContextPort loadBotContextPort(
            LoadOrderBookUseCase tickers, LoadMarketDataUseCase candles,
            TradingProperties properties, PaperBrokerAdapter paperBrokerAdapter) {
        return new PaperBotContextAdapter(
                new MarketBotContextAdapter(tickers, candles, properties), paperBrokerAdapter);
    }

    /**
     * 진입 수수료를 테이커로 잡는다. <b>봇은 지정가를 내지 않으므로 메이커가 될 수 없다</b> —
     * 백테스트가 메이커를 쓰는 자리와 다른 것은 주문 종류가 다르기 때문이지 정책이 갈린 것이
     * 아니다.
     */
    private static CostModel costsFrom(TradingProperties properties) {
        return new CostModel(
                properties.takerFee(), properties.takerFee(), properties.slippage());
    }
}
