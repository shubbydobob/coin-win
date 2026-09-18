package com.coinwin.trading.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.binance.BinanceServerClock;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.application.port.out.PlaceOrderPortContract;
import com.coinwin.trading.domain.TradingMode;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 거래소 어댑터가 <b>장부 브로커와 같은 계약</b>을 지키는가. 그리고 실제로 무엇을 보내는가.
 *
 * <p>뒤쪽이 이 클래스에만 있는 이유는, 장부 브로커에는 "보내는 것" 이 없기 때문이다.
 * <b>이 변환이 틀리면 돈이 잘못 움직인다</b> — 손절을 낸다고 하고 시장가를 보내면 포지션이
 * 그 자리에서 닫힌다.
 */
class BinanceOrderAdapterContractTest extends PlaceOrderPortContract {

    private FakeOrderExchange exchange;
    private BinanceOrderAdapter adapter;

    @AfterEach
    void closeExchange() {
        if (exchange != null) {
            exchange.close();
        }
    }

    @Override
    protected PlaceOrderPort port() {
        if (adapter == null) {
            exchange = new FakeOrderExchange();
            adapter = new BinanceOrderAdapter(
                    exchange.client(),
                    new BinanceOrderCredentials("key", "secret"),
                    new BinanceServerClock(exchange.client(), Clock.systemUTC()),
                    TradingMode.TESTNET);
        }
        return adapter;
    }

    /** 롱을 닫는 것은 매도다. 부호를 뒤집으면 손절이 포지션을 두 배로 만든다. */
    @Test
    void 롱의_손절은_매도로_나간다() {
        port().place(stop());

        assertThat(lastRequest()).contains("side=SELL").contains("type=STOP_MARKET");
    }

    /** 롱을 여는 것은 매수다. */
    @Test
    void 롱_진입은_매수_시장가로_나간다() {
        port().place(entry());

        assertThat(lastRequest()).contains("side=BUY").contains("type=MARKET")
                .contains("quantity=0.01000000");
    }

    /**
     * <b>청산이 마크 가격으로 일어나므로 손절도 같은 자를 써야 한다.</b> 체결가 기준 손절은
     * 꼬리에 스친다 — {@code exit-automation.md} 의 R1.
     */
    @Test
    void 트리거_주문은_마크_가격을_기준으로_건다() {
        port().place(stop());

        assertThat(lastRequest()).contains("workingType=MARK_PRICE")
                .contains("stopPrice=76440.00");
    }

    /** 수량이 어긋나도 남는 포지션이 없다. 그래서 수량과 함께 보내면 거래소가 거절한다. */
    @Test
    void 전량_청산은_closePosition_으로_나가고_수량을_함께_보내지_않는다() {
        port().place(stop());

        assertThat(lastRequest()).contains("closePosition=true").doesNotContain("quantity=");
    }

    /**
     * 지금 닫는 주문도 전량이라 {@code closePosition} 으로 나간다. 줄이는 주문은 포지션을
     * 뒤집을 수 없다 — 자동으로 도는 루프에서 그 보장은 값이 크다.
     */
    @Test
    void 지금_닫는_주문도_전량으로_나간다() {
        port().place(exit());

        assertThat(lastRequest()).contains("side=SELL").contains("type=MARKET")
                .contains("closePosition=true");
    }

    @Test
    void 서명과_타임스탬프가_붙는다() {
        port().place(entry());

        assertThat(lastRequest()).contains("signature=").contains("timestamp=")
                .contains("recvWindow=");
    }

    /** 테스트넷인지 실계좌인지를 객체가 알아야 기록에도 남는다. */
    @Test
    void 모드를_들고_다닌다() {
        assertThat(port().mode()).isEqualTo(TradingMode.TESTNET);
        assertThat(port().mode().movesRealMoney()).isFalse();
    }

    /** 장부 모드는 이 어댑터의 일이 아니다. 섞이면 "돈이 안 든다" 는 판단이 흐려진다. */
    @Test
    void 장부_모드로는_만들_수_없다() {
        FakeOrderExchange fake = new FakeOrderExchange();
        try {
            assertThatThrownBy(() -> new BinanceOrderAdapter(
                    fake.client(), new BinanceOrderCredentials("k", "s"),
                    new BinanceServerClock(fake.client(), Clock.systemUTC()), TradingMode.PAPER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PaperBrokerAdapter");
        } finally {
            fake.close();
        }
    }

    private String lastRequest() {
        return exchange.requests().getLast();
    }
}
