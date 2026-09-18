package com.coinwin.trading.adapter.out;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.trading.adapter.out.market.MarketBotContextAdapter;
import com.coinwin.trading.adapter.out.paper.PaperBrokerAdapter;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.domain.HoldStrategy;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.TradingMode;
import com.coinwin.trading.domain.TradingStrategy;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
@EnableConfigurationProperties(TradingProperties.class)
public class TradingConfig {

    /**
     * 기본 전략은 아무것도 하지 않는 것이다.
     *
     * <p>임시 구현이 아니라 옳은 기본값이다 — 이 저장소가 두 번 쟀고 두 번 다 진입 규칙이
     * 없다고 나왔다({@code docs/adr/021} · {@code docs/adr/022}).
     */
    @Bean
    TradingStrategy tradingStrategy() {
        return new HoldStrategy();
    }

    /** 자리표시자 한계. 근거 있는 수가 아니라는 것이 {@code RiskLimits} 에 적혀 있다. */
    @Bean
    RiskLimits riskLimits() {
        return RiskLimits.placeholder();
    }

    /**
     * 장부 브로커. <b>백테스트와 같은 {@link CostModel} 을 쓴다</b> — 두 기록을 나란히 놓는
     * 것이 실계좌로 갈 수 있는지를 정하는 유일한 시험이라 다른 자로 재면 무의미해진다.
     */
    @Bean
    PlaceOrderPort placeOrderPort(TradingProperties properties) {
        return new PaperBrokerAdapter(costsFrom(properties), Clock.systemUTC());
    }

    @Bean
    LoadBotContextPort loadBotContextPort(
            LoadOrderBookUseCase tickers, LoadMarketDataUseCase candles,
            TradingProperties properties) {
        return new MarketBotContextAdapter(tickers, candles, properties);
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
