package com.coinwin.market.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.market.domain.OrderBook;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.Ticker;
import org.junit.jupiter.api.Test;

/**
 * 호가 포트의 계약. <b>바이낸스 어댑터와 인메모리 어댑터가 모두 이 스위트를 통과해야 한다.</b>
 *
 * <p>어댑터마다 테스트를 따로 쓰면 각자 자기 구현이 하는 일을 검사하게 되고, 포트가 하나의
 * 약속인지는 아무도 확인하지 않는다. 근거: {@code .claude/docs/testing.md}
 *
 * <p><b>값을 단언하지 않는다.</b> 호가는 매 순간 달라지므로 "최우선 매수가가 76,567" 같은
 * 단언은 인메모리에서만 성립한다. 여기서 확인하는 것은 <b>어떤 구현이든 반드시 참인 성질</b>
 * 이다 — 매도가 매수보다 높고, 요청한 단수를 넘지 않고, 없는 종목은 실패한다.
 */
public abstract class OrderBookPortContract {

    protected static final Symbol SYMBOL = Symbol.of("BTCUSDT");

    protected abstract LoadOrderBookPort port();

    @Test
    void 호가는_요청한_단수를_넘지_않는다() {
        OrderBook book = port().orderBookFor(SYMBOL, 5);

        assertThat(book.bids()).hasSizeLessThanOrEqualTo(5).isNotEmpty();
        assertThat(book.asks()).hasSizeLessThanOrEqualTo(5).isNotEmpty();
    }

    @Test
    void 최우선_매도가는_최우선_매수가보다_낮지_않다() {
        OrderBook book = port().orderBookFor(SYMBOL, 20);

        assertThat(book.bestAsk().isBelow(book.bestBid())).isFalse();
        assertThat(book.spread().value().signum()).isNotNegative();
    }

    @Test
    void 매수는_내림차순_매도는_오름차순이다() {
        OrderBook book = port().orderBookFor(SYMBOL, 20);

        assertThat(book.bids()).isSortedAccordingTo(
                (a, b) -> b.price().value().compareTo(a.price().value()));
        assertThat(book.asks()).isSortedAccordingTo(
                (a, b) -> a.price().value().compareTo(b.price().value()));
    }

    @Test
    void 불균형은_마이너스1과_1_사이다() {
        assertThat(port().orderBookFor(SYMBOL, 20).imbalance())
                .isBetween(java.math.BigDecimal.valueOf(-1), java.math.BigDecimal.ONE);
    }

    @Test
    void 시세의_현재가는_하루_고저_안에_있다() {
        Ticker ticker = port().tickerFor(SYMBOL);

        assertThat(ticker.last().isBelow(ticker.low24h())).isFalse();
        assertThat(ticker.high24h().isBelow(ticker.last())).isFalse();
        assertThat(ticker.symbol()).isEqualTo(SYMBOL);
    }

    /** 종목을 모르면 조용히 빈 호가를 내지 않는다 — 빈 것과 없는 것은 다른 사실이다. */
    @Test
    void 모르는_종목은_실패한다() {
        Symbol 없는것 = Symbol.of("NOSUCHPAIR");

        assertThatThrownBy(() -> port().orderBookFor(없는것, 5)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> port().tickerFor(없는것)).isInstanceOf(RuntimeException.class);
    }
}
