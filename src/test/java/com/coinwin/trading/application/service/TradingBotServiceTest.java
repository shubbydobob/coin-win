package com.coinwin.trading.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.adapter.out.paper.PaperBrokerAdapter;
import com.coinwin.trading.domain.AccountState;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.BotPosition;
import com.coinwin.trading.domain.CycleDecision;
import com.coinwin.trading.domain.MarketReading;
import com.coinwin.trading.domain.MarketView;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.TradingCycle;
import com.coinwin.trading.domain.TradingMode;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.TradingStrategy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 한 사이클의 순서 — 읽고 · 묻고 · 통과시키고 · 내고 · 기록한다.
 *
 * <p><b>키도 DB 도 거래소도 없이 전부 돈다.</b> 장부 브로커와 손으로 만든 컨텍스트만 쓴다.
 */
class TradingBotServiceTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final CostModel FREE = CostModel.free();

    private final PaperBrokerAdapter broker =
            new PaperBrokerAdapter(FREE, Clock.fixed(AT, ZoneOffset.UTC), EQUITY);

    /** 대부분의 사이클이 이렇다. 그리고 그 침묵이 기록에 남는다. */
    @Test
    void 아무것도_안_하는_전략이면_조용한_사이클이_된다() {
        TradingCycle cycle = run(new StubStrategy(List.of()), AccountState.flat(EQUITY));

        assertThat(cycle.quiet()).isTrue();
        assertThat(cycle.placed()).isEmpty();
        assertThat(cycle.strategy()).isEqualTo("스텁");
    }

    @Test
    void 통과한_주문만_브로커에_닿는다() {
        TradingCycle cycle = run(new StubStrategy(List.of(entry("0.01"))),
                AccountState.flat(EQUITY));

        assertThat(cycle.placed()).hasSize(1);
        assertThat(cycle.rejected()).isEmpty();
        assertThat(cycle.mode()).isEqualTo(TradingMode.PAPER);
    }

    /**
     * <b>막힌 것과 아무것도 안 한 것은 다른 사실이다.</b> 거부가 기록에 남지 않으면
     * "봇이 왜 안 들어갔나" 가 답할 수 없는 질문이 된다.
     */
    @Test
    void 한계에_걸린_주문은_내지_않고_이유를_남긴다() {
        TradingCycle cycle = run(new StubStrategy(List.of(entry("0.5"))),
                AccountState.flat(EQUITY));

        assertThat(cycle.placed()).isEmpty();
        assertThat(cycle.rejected()).singleElement()
                .satisfies(rejected -> assertThat(rejected.reason()).contains("명목"));
        assertThat(cycle.quiet()).isFalse();
    }

    @Test
    void 일부만_막히면_나머지는_나간다() {
        TradingCycle cycle = run(new StubStrategy(List.of(entry("0.5"), stop())),
                new AccountState(EQUITY, 1, Money.of("0"), Money.of("0")));

        assertThat(cycle.rejected()).hasSize(1);
        assertThat(cycle.placed()).hasSize(1);
    }

    /**
     * <b>누적 손실 한계는 전략에게 묻기도 전에 본다.</b> 물어 놓고 전부 거부하면 기록이
     * "전략이 내려 했는데 막혔다" 로 읽히는데, 사실은 봇이 꺼져 있어야 하는 상태다.
     */
    @Test
    void 누적_손실_한계를_넘으면_전략에게_묻지도_않는다() {
        AccountState broken = new AccountState(EQUITY, 0, Money.of("0"), Money.of("-200"));
        StubStrategy strategy = new StubStrategy(List.of(entry("0.01")));

        TradingCycle cycle = run(strategy, broken);

        assertThat(strategy.asked).isFalse();
        assertThat(cycle.halted()).isPresent();
        assertThat(cycle.placed()).isEmpty();
    }

    /**
     * <b>던지지 않는다.</b> 자동으로 도는 루프에서 예외가 올라가면 다음 사이클이 돌지 안 돌지가
     * 스케줄러의 사정이 된다. 실패도 기록에 남아야 한다.
     */
    @Test
    void 거래소를_못_읽어도_던지지_않고_사이클에_담는다() {
        TradingCycle cycle = new TradingBotService(new StubStrategy(List.of()), broker,
                symbol -> {
                    throw new ExternalDataUnavailableException("거래소가 대답하지 않는다");
                },
                RiskLimits.placeholder()).runOnce();

        assertThat(cycle.halted()).isPresent();
        assertThat(cycle.halted().orElseThrow()).contains("읽지 못했다");
        assertThat(cycle.placed()).isEmpty();
    }

    /** 전략은 열린 포지션을 본다. 안 보면 같은 자리에 또 들어간다. */
    @Test
    void 전략에게_열린_포지션을_보여_준다() {
        StubStrategy strategy = new StubStrategy(List.of());
        BotPosition open = new BotPosition(Direction.SHORT, Quantity.of("0.1"), Price.of("78000"));

        new TradingBotService(strategy, broker,
                symbol -> context(new AccountState(EQUITY, 1, Money.of("0"), Money.of("0")), open),
                RiskLimits.placeholder()).runOnce();

        assertThat(strategy.sawPosition).contains(open);
    }

    private TradingCycle run(TradingStrategy strategy, AccountState account) {
        return new TradingBotService(strategy, broker,
                symbol -> context(account, null), RiskLimits.placeholder()).runOnce();
    }

    private static BotContext context(AccountState account, BotPosition open) {
        return new BotContext(
                new MarketView(Symbol.BTC_USDT, Price.of("78000"), List.of(), AT),
                MarketReading.none(),
                account, Optional.ofNullable(open), List.of());
    }

    private static OrderIntent entry(String quantity) {
        return OrderIntent.entry(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of(quantity), Price.of("78000")));
    }

    private static OrderIntent stop() {
        return OrderIntent.protectAll(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Protection(
                        com.coinwin.trading.domain.OrderKind.STOP_LOSS,
                        Price.of("77000"), Price.of("78000")));
    }

    /** 정해진 주문을 그대로 내는 전략. 물어봤는지와 무엇을 봤는지를 기억한다. */
    private static final class StubStrategy implements TradingStrategy {

        private final List<OrderIntent> intents;
        private boolean asked;
        private Optional<BotPosition> sawPosition = Optional.empty();

        private StubStrategy(List<OrderIntent> intents) {
            this.intents = intents;
        }

        @Override
        public String name() {
            return "스텁";
        }

        @Override
        public CycleDecision decide(BotContext now) {
            asked = true;
            sawPosition = now.open();
            return new CycleDecision(intents, List.of());
        }
    }
}
