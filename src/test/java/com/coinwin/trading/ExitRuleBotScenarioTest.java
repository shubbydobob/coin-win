package com.coinwin.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.CostModel;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.adapter.out.paper.PaperBotContextAdapter;
import com.coinwin.trading.adapter.out.paper.PaperBrokerAdapter;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.application.service.TradingBotService;
import com.coinwin.trading.domain.AccountState;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.ExitRuleStrategy;
import com.coinwin.trading.domain.MarketView;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.OrderKind;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.TradingCycle;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 봇 한 대를 실제로 돌린다 — 진짜 서비스 · 진짜 장부 브로커 · 진짜 전략. 시장만 손으로 민다.
 *
 * <p><b>이 스위트가 증명하려는 문장은 하나다:</b> 1차 익절이 체결되고 손절이 본전으로
 * 옮겨진 뒤에는 <b>그 거래가 손실로 끝날 수 없다.</b> 그것이 R4 를 만든 이유이고,
 * "300~400달러 벌고 있다가 다 반납" 하는 경로가 사라지는 자리다.
 *
 * <p>단위 테스트들은 각 조각이 규칙을 따르는지만 본다. 조각이 다 맞는데 <b>이어 붙이면
 * 안 도는</b> 경우는 이런 식으로 끝까지 밀어 봐야 드러난다 — 이 저장소가 브라우저 실측에서
 * 두 번 겪은 것과 같은 종류다.
 */
class ExitRuleBotScenarioTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final Price ENTRY = Price.of("78000");

    /** 비용 0 으로 둔다. 손익의 부호만 보는 스위트라 수수료가 섞이면 무엇이 무엇인지 흐려진다. */
    private final PaperBrokerAdapter broker =
            new PaperBrokerAdapter(CostModel.free(), Clock.fixed(AT, ZoneOffset.UTC), EQUITY);
    private final MovableMarket market = new MovableMarket(ENTRY);
    private final TradingBotService bot = new TradingBotService(
            new ExitRuleStrategy(Percentage.of("2"), Percentage.of("2"), Percentage.of("0.14")),
            broker,
            new PaperBotContextAdapter(market, broker),
            RiskLimits.placeholder());

    /**
     * <b>이 시나리오가 이 클래스의 전부다.</b> 들어가고 · 규칙이 붙고 · 목표에 닿고 ·
     * 되돌아와 본전에서 끊긴다. 마지막에 손익이 음수가 아니다.
     */
    @Test
    void 익절_뒤_되돌아와도_손실로_끝나지_않는다() {
        humanEnters(Direction.LONG);

        bot.runOnce();                                   // 규칙이 손절과 절반 익절을 건다
        market.moveTo(Price.of("79560"));                // 1차 목표에 닿는다
        bot.runOnce();                                   // 절반 체결 · 손절이 본전으로
        market.moveTo(Price.of("78050"));                // 되돌아온다 — 본전 손절에 닿는다
        bot.runOnce();

        assertThat(broker.ledger().position()).isEmpty();
        assertThat(broker.ledger().realizedTotal().isNegative()).isFalse();
    }

    /** 첫 사이클에 둘이 걸린다 — 손절 전량, 익절 절반. */
    @Test
    void 첫_사이클에_손절과_절반_익절이_걸린다() {
        humanEnters(Direction.LONG);

        TradingCycle cycle = bot.runOnce();

        assertThat(cycle.placed()).hasSize(2);
        assertThat(triggerOf(OrderKind.STOP_LOSS)).isEqualTo(Price.of("76440.00"));
        assertThat(broker.restingOrders())
                .filteredOn(order -> order.intent().kind() == OrderKind.TAKE_PROFIT)
                .singleElement()
                .satisfies(order -> assertThat(order.intent().quantity())
                        .contains(Quantity.of("0.05")));
    }

    /** 가만히 있으면 아무것도 안 한다. 사이클마다 같은 주문을 다시 내면 손절이 쌓인다. */
    @Test
    void 아무_일도_없으면_두_번째_사이클은_조용하다() {
        humanEnters(Direction.LONG);
        bot.runOnce();

        assertThat(bot.runOnce().quiet()).isTrue();
    }

    /** <b>R4.</b> 목표에 닿으면 옛 손절이 지워지고 본전 손절이 걸린다. */
    @Test
    void 목표에_닿으면_손절이_본전으로_옮겨진다() {
        humanEnters(Direction.LONG);
        bot.runOnce();
        market.moveTo(Price.of("79560"));

        TradingCycle cycle = bot.runOnce();

        assertThat(cycle.cancelled()).hasSize(1);
        assertThat(triggerOf(OrderKind.STOP_LOSS)).isEqualTo(Price.of("78109.20"));
    }

    /** 손절이 둘 남으면 다음 진입이 앞 거래의 손절을 물려받는다. */
    @Test
    void 옮긴_뒤에_손절이_하나만_남는다() {
        humanEnters(Direction.LONG);
        bot.runOnce();
        market.moveTo(Price.of("79560"));
        bot.runOnce();

        assertThat(broker.restingOrders())
                .filteredOn(order -> order.intent().kind() == OrderKind.STOP_LOSS)
                .hasSize(1);
    }

    /** 숏도 같다. 부호만 전부 뒤집힌다. */
    @Test
    void 숏도_익절_뒤에는_손실로_끝나지_않는다() {
        humanEnters(Direction.SHORT);

        bot.runOnce();
        market.moveTo(Price.of("76440"));
        bot.runOnce();
        market.moveTo(Price.of("77950"));
        bot.runOnce();

        assertThat(broker.ledger().position()).isEmpty();
        assertThat(broker.ledger().realizedTotal().isNegative()).isFalse();
    }

    /** 목표에 못 닿고 손절로 끝나는 쪽. 손실은 나지만 <b>한계 안에서</b> 끝난다. */
    @Test
    void 목표에_못_닿으면_처음_손절에서_끊긴다() {
        humanEnters(Direction.LONG);
        bot.runOnce();

        market.moveTo(Price.of("76400"));
        bot.runOnce();

        assertThat(broker.ledger().position()).isEmpty();
        assertThat(Money.of("200").isGreaterThan(broker.ledger().state().lostTotal())).isTrue();
    }

    /** 사람이 진입한다. 봇은 진입하지 않는다 — 그것이 이 전략의 전제다. */
    private void humanEnters(Direction direction) {
        broker.place(OrderIntent.entry(Symbol.BTC_USDT, direction,
                new OrderIntent.Sizing(Quantity.of("0.1"), ENTRY)));
    }

    private Price triggerOf(OrderKind kind) {
        return broker.restingOrders().stream()
                .filter(order -> order.intent().kind() == kind)
                .map(order -> order.intent().trigger().orElseThrow())
                .findFirst().orElseThrow();
    }

    /** 시장만 손으로 미는 어댑터. 계좌는 장부가 얹는다. */
    private static final class MovableMarket implements LoadBotContextPort {

        private Price mark;

        private MovableMarket(Price mark) {
            this.mark = mark;
        }

        private void moveTo(Price price) {
            this.mark = price;
        }

        @Override
        public BotContext contextFor(Symbol symbol) {
            return new BotContext(
                    new MarketView(symbol, mark, List.of(), AT),
                    AccountState.flat(EQUITY), Optional.empty(), List.of());
        }
    }
}
