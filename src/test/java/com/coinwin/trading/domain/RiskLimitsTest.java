package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.Direction;
import org.junit.jupiter.api.Test;

/**
 * 전략이 뚫을 수 없는 벽.
 *
 * <p>이 스위트가 이 모듈에서 가장 중요하다 — 자동으로 도는 루프에서 여기 구멍이 나면
 * 사람이 보고 있지 않은 동안 계좌가 빈다.
 */
class RiskLimitsTest {

    private static final Money EQUITY = Money.of("800");
    private static final RiskLimits LIMITS = RiskLimits.placeholder();

    @Test
    void 한계_안의_진입은_통과한다() {
        RiskVerdict verdict = LIMITS.judge(entry("0.01", "78000"), AccountState.flat(EQUITY));

        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict).isInstanceOf(RiskVerdict.Allowed.class);
    }

    /** 자산 800 × 2배 = 1,600 이 한계다. 0.03 BTC × 78,000 = 2,340 은 넘는다. */
    @Test
    void 명목이_계좌의_두_배를_넘으면_거부한다() {
        RiskVerdict verdict = LIMITS.judge(entry("0.03", "78000"), AccountState.flat(EQUITY));

        assertThat(verdict.allowed()).isFalse();
        assertThat(reason(verdict)).contains("명목").contains("한계를 넘는다");
    }

    @Test
    void 이미_포지션이_있으면_새로_열지_않는다() {
        AccountState busy = new AccountState(EQUITY, 1, Money.of("0"), Money.of("0"));

        assertThat(reason(LIMITS.judge(entry("0.01", "78000"), busy)))
                .contains("동시 포지션 한계");
    }

    /** 800 의 5% 는 40. 41 을 잃었으면 그날은 새로 안 들어간다. */
    @Test
    void 오늘_손실이_한계를_넘으면_새로_열지_않는다() {
        AccountState bled = new AccountState(EQUITY, 0, Money.of("-41"), Money.of("-41"));

        assertThat(reason(LIMITS.judge(entry("0.01", "78000"), bled)))
                .contains("오늘 손실");
    }

    /** 800 의 20% 는 160. 넘으면 날이 바뀌어도 사람이 켜야 다시 돈다. */
    @Test
    void 누적_손실이_한계를_넘으면_봇을_세운다() {
        AccountState broken = new AccountState(EQUITY, 0, Money.of("0"), Money.of("-161"));

        assertThat(LIMITS.halts(broken)).isTrue();
        assertThat(reason(LIMITS.judge(entry("0.01", "78000"), broken)))
                .contains("사람이 켜야");
    }

    @Test
    void 손실이_한계에_닿기만_하면_아직_거부하지_않는다() {
        AccountState atLimit = new AccountState(EQUITY, 0, Money.of("-40"), Money.of("-40"));

        assertThat(LIMITS.judge(entry("0.01", "78000"), atLimit).allowed()).isTrue();
    }

    /**
     * <b>줄이는 주문은 언제나 통과한다.</b> 손실 한계에 걸렸다고 손절을 못 걸면 그 한계가
     * 정확히 막으려던 일이 일어난다 — 한계는 새 위험을 막는 것이지 이미 열린 위험을 가두는
     * 것이 아니다.
     */
    @Test
    void 손실_한계에_걸려도_손절은_걸_수_있다() {
        AccountState broken = new AccountState(EQUITY, 1, Money.of("-500"), Money.of("-500"));

        assertThat(LIMITS.judge(stop("79500"), broken).allowed()).isTrue();
    }

    @Test
    void 포지션이_다_차_있어도_닫는_것은_할_수_있다() {
        AccountState busy = new AccountState(EQUITY, 1, Money.of("0"), Money.of("0"));
        OrderIntent exit = new OrderIntent(Symbol.BTC_USDT, Direction.SHORT, OrderKind.EXIT,
                java.util.Optional.empty(), java.util.Optional.empty(), Price.of("78000"));

        assertThat(LIMITS.judge(exit, busy).allowed()).isTrue();
    }

    /** 이익이 나고 있으면 손실은 0 이다. 한계는 손실만 본다. */
    @Test
    void 이익_중에는_손실_한계에_걸리지_않는다() {
        AccountState winning = new AccountState(EQUITY, 0, Money.of("300"), Money.of("300"));

        assertThat(winning.lostToday()).isEqualTo(Money.of("0"));
        assertThat(LIMITS.halts(winning)).isFalse();
    }

    private static OrderIntent entry(String quantity, String price) {
        return OrderIntent.entry(Symbol.BTC_USDT, Direction.LONG,
                new OrderIntent.Sizing(Quantity.of(quantity), Price.of(price)));
    }

    private static OrderIntent stop(String trigger) {
        return OrderIntent.protectAll(Symbol.BTC_USDT, Direction.SHORT,
                new OrderIntent.Protection(
                        OrderKind.STOP_LOSS, Price.of(trigger), Price.of("78000")));
    }

    private static String reason(RiskVerdict verdict) {
        return ((RiskVerdict.Rejected) verdict).reason();
    }
}
