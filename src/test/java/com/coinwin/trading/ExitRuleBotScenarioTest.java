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
import com.coinwin.trading.domain.CallbackRate;
import com.coinwin.trading.domain.ExitRuleStrategy;
import com.coinwin.trading.domain.MarketReading;
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
 * <p><b>이 스위트가 증명하려는 문장은 둘이다.</b> 1차 익절이 체결되고 손절이 본전으로
 * 옮겨진 뒤에는 <b>그 거래가 손실로 끝날 수 없고</b>(R4), 추세가 이어지면 <b>추격이 본전보다
 * 많이 남긴다</b>(R5). 앞이 § 0 의 −2,000 을, 뒤가 "300~400달러 벌고 있다가 다 반납" 을
 * 가리킨다.
 *
 * <p>단위 테스트들은 각 조각이 규칙을 따르는지만 본다. 조각이 다 맞는데 <b>이어 붙이면
 * 안 도는</b> 경우는 이런 식으로 끝까지 밀어 봐야 드러난다 — 이 저장소가 브라우저 실측에서
 * 두 번 겪은 것과 같은 종류다.
 */
class ExitRuleBotScenarioTest {

    private static final Instant AT = Instant.parse("2026-09-18T03:00:00Z");
    private static final Money EQUITY = Money.of("800");
    private static final Price ENTRY = Price.of("78000");
    private static final Percentage DISTANCE = Percentage.of("2");
    private static final Percentage COST = Percentage.of("0.14");

    private final Rig rig = rig(Optional.empty());

    /**
     * <b>이 시나리오가 R4 의 전부다.</b> 들어가고 · 규칙이 붙고 · 목표에 닿고 ·
     * 되돌아와 본전에서 끊긴다. 마지막에 손익이 음수가 아니다.
     */
    @Test
    void 익절_뒤_되돌아와도_손실로_끝나지_않는다() {
        rig.enters(Direction.LONG);

        rig.bot().runOnce();                             // 규칙이 손절과 절반 익절을 건다
        rig.walkTo("79560");                             // 1차 목표에 닿는다 — 절반 체결
        rig.walkTo("78050");                             // 되돌아온다 — 본전 손절에 닿는다

        assertThat(rig.broker().ledger().position()).isEmpty();
        assertThat(rig.broker().ledger().realizedTotal().isNegative()).isFalse();
    }

    /** 첫 사이클에 둘이 걸린다 — 손절 전량, 익절 절반. */
    @Test
    void 첫_사이클에_손절과_절반_익절이_걸린다() {
        rig.enters(Direction.LONG);

        TradingCycle cycle = rig.bot().runOnce();

        assertThat(cycle.placed()).hasSize(2);
        assertThat(rig.triggerOf(OrderKind.STOP_LOSS)).isEqualTo(Price.of("76440.00"));
        assertThat(rig.restingOf(OrderKind.TAKE_PROFIT))
                .singleElement()
                .satisfies(order -> assertThat(order.intent().quantity())
                        .contains(Quantity.of("0.05")));
    }

    /** 가만히 있으면 아무것도 안 한다. 사이클마다 같은 주문을 다시 내면 손절이 쌓인다. */
    @Test
    void 아무_일도_없으면_두_번째_사이클은_조용하다() {
        rig.enters(Direction.LONG);
        rig.bot().runOnce();

        assertThat(rig.bot().runOnce().quiet()).isTrue();
    }

    /** <b>R4.</b> 목표에 닿으면 옛 손절이 지워지고 본전 손절이 걸린다. */
    @Test
    void 목표에_닿으면_손절이_본전으로_옮겨진다() {
        rig.enters(Direction.LONG);
        rig.bot().runOnce();
        rig.market().moveTo(Price.of("79560"));

        TradingCycle cycle = rig.bot().runOnce();

        assertThat(cycle.cancelled()).hasSize(1);
        assertThat(rig.triggerOf(OrderKind.STOP_LOSS)).isEqualTo(Price.of("78109.20"));
    }

    /** 손절이 둘 남으면 다음 진입이 앞 거래의 손절을 물려받는다. */
    @Test
    void 옮긴_뒤에_손절이_하나만_남는다() {
        rig.enters(Direction.LONG);
        rig.bot().runOnce();
        rig.walkTo("79560");

        assertThat(rig.restingOf(OrderKind.STOP_LOSS)).hasSize(1);
    }

    /** 숏도 같다. 부호만 전부 뒤집힌다. */
    @Test
    void 숏도_익절_뒤에는_손실로_끝나지_않는다() {
        rig.enters(Direction.SHORT);

        rig.bot().runOnce();
        rig.walkTo("76440");
        rig.walkTo("77950");

        assertThat(rig.broker().ledger().position()).isEmpty();
        assertThat(rig.broker().ledger().realizedTotal().isNegative()).isFalse();
    }

    /** 목표에 못 닿고 손절로 끝나는 쪽. 손실은 나지만 <b>한계 안에서</b> 끝난다. */
    @Test
    void 목표에_못_닿으면_처음_손절에서_끊긴다() {
        rig.enters(Direction.LONG);
        rig.bot().runOnce();

        rig.walkTo("76400");

        assertThat(rig.broker().ledger().position()).isEmpty();
        assertThat(Money.of("200").isGreaterThan(rig.broker().ledger().state().lostTotal()))
                .isTrue();
    }

    /**
     * <b>R5 가 존재하는 이유가 이 테스트다.</b> 같은 길을 두 봇이 걷는다 — 목표를 지나
     * 85,000 까지 갔다가 본전까지 통째로 되돌아온다.
     *
     * <p>절반 익절은 둘 다 78.00 으로 같다. <b>갈리는 것은 남은 절반</b>이고, 추격은
     * 최고점에서 1% 떨어진 80,000 에서 +100.00 을, 본전 손절은 78,109.20 에서 +5.46 을 낸다.
     * 사용자의 말로 "300~400달러 벌고 있다가 다 반납" 하는 자리가 이 5.46 이다.
     */
    @Test
    void 추세가_이어지면_추격이_본전보다_많이_남긴다() {
        Rig trailed = rig(Optional.of(CallbackRate.of("1")));
        rig.enters(Direction.LONG);
        trailed.enters(Direction.LONG);
        rig.bot().runOnce();                             // 규칙이 붙는다 — 절반 익절이 걸린다
        trailed.bot().runOnce();

        for (String price : List.of("79560", "85000", "80000", "78109.20")) {
            rig.walkTo(price);
            trailed.walkTo(price);
        }

        assertThat(trailed.broker().ledger().position()).isEmpty();
        assertThat(rig.broker().ledger().position()).isEmpty();
        // 절반 익절 +78.00 은 둘이 같다. 갈리는 것은 남은 절반이다 —
        // 추격은 80,000 에서 +100.00, 본전은 78,109.20 에서 +5.46.
        assertThat(trailed.broker().ledger().realizedTotal()).isEqualTo(Money.of("178.00"));
        assertThat(rig.broker().ledger().realizedTotal()).isEqualTo(Money.of("83.46"));
    }

    /**
     * <b>R5 가 R4 를 무르지 않는다.</b> 추격 폭이 10% 면 그 손절은 진입가보다 한참 아래에
     * 놓이는데, 본전 손절이 함께 남아 있으므로 <b>언제나 위쪽이 먼저 받는다.</b>
     *
     * <p>이것이 추격으로 본전을 <i>대신하지 않은</i> 이유 전부다 — 대신했으면 이 거래는
     * 폭에 따라 다시 손실로 끝날 수 있다.
     */
    @Test
    void 추격이_넓어도_본전_손절이_먼저_받는다() {
        Rig wide = rig(Optional.of(CallbackRate.of("10")));
        wide.enters(Direction.LONG);
        wide.bot().runOnce();

        wide.walkTo("79560");
        wide.walkTo("78000");

        assertThat(wide.broker().ledger().position()).isEmpty();
        assertThat(wide.broker().ledger().realizedTotal().isNegative()).isFalse();
    }

    /** 최고점이 새로 서는 걸음에서는 추격이 터지지 않는다. 올라가는 봉의 손절은 고장이다. */
    @Test
    void 최고점을_새로_쓰는_동안에는_추격이_터지지_않는다() {
        Rig trailed = rig(Optional.of(CallbackRate.of("1")));
        trailed.enters(Direction.LONG);
        trailed.bot().runOnce();

        trailed.walkTo("79560");
        trailed.walkTo("82000");
        trailed.walkTo("90000");

        assertThat(trailed.broker().ledger().position()).isPresent();
        assertThat(trailed.restingOf(OrderKind.TRAILING_STOP)).hasSize(1);
    }

    private static Rig rig(Optional<CallbackRate> trailing) {
        PaperBrokerAdapter broker = new PaperBrokerAdapter(
                CostModel.free(), Clock.fixed(AT, ZoneOffset.UTC), EQUITY);
        MovableMarket market = new MovableMarket(ENTRY);
        return new Rig(broker, market, new TradingBotService(
                new ExitRuleStrategy(DISTANCE, DISTANCE, COST, trailing),
                broker, new PaperBotContextAdapter(market, broker), RiskLimits.placeholder()));
    }

    /** 봇 한 대와 그것이 보는 시장·장부. 비교 테스트가 두 대를 같은 길에 올린다. */
    private record Rig(PaperBrokerAdapter broker, MovableMarket market, TradingBotService bot) {

        /** 사람이 진입한다. 봇은 진입하지 않는다 — 그것이 이 전략의 전제다. */
        void enters(Direction direction) {
            broker.place(OrderIntent.entry(Symbol.BTC_USDT, direction,
                    new OrderIntent.Sizing(Quantity.of("0.1"), ENTRY)));
        }

        /** 가격이 여기까지 왔고 봇이 한 번 깨어난다. */
        void walkTo(String price) {
            market.moveTo(Price.of(price));
            bot.runOnce();
        }

        List<com.coinwin.trading.domain.PlacedOrder> restingOf(OrderKind kind) {
            return broker.restingOrders().stream()
                    .filter(order -> order.intent().kind() == kind)
                    .toList();
        }

        Price triggerOf(OrderKind kind) {
            return restingOf(kind).getFirst().intent().trigger().orElseThrow();
        }
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
                MarketReading.none(),
                    AccountState.flat(EQUITY), Optional.empty(), List.of());
        }
    }
}
