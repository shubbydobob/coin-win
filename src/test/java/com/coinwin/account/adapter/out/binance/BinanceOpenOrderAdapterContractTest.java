package com.coinwin.account.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.account.application.port.out.LoadOpenOrdersPort;
import com.coinwin.account.application.port.out.OpenOrdersPortContract;
import com.coinwin.market.domain.Symbol;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 바이낸스 어댑터가 <b>인메모리 어댑터와 같은 계약</b>을 지키는가.
 *
 * <p>실제 거래소를 때리지 않는다 — 이 엔드포인트는 서명이 필요해 실계좌 없이는 응답을 볼 수
 * 없고, 그러면 {@code check} 가 키에 묶인다. 대신 <b>거래소 응답 원문</b>을 로컬 서버에 물린다.
 *
 * <p>여기 적힌 JSON 이 바이낸스 문서의 모양 그대로여야 이 스위트가 뜻을 갖는다. 그것이
 * 진짜 거래소와 같은가는 이 테스트가 증명하지 못하고, 실계좌로 사람이 한 번 보는 것 말고는
 * 방법이 없다 — Phase 8 이 배운 <i>"자기 자신을 근거로 삼은 검증은 무엇도 증명하지 않는다"</i>
 * 가 걸리는 자리라 여기 적어 둔다.
 */
class BinanceOpenOrderAdapterContractTest extends OpenOrdersPortContract {

    /**
     * 장면. 마지막 한 줄은 <b>포지션을 여는</b> 지정가라 나오면 안 된다 —
     * {@code reduceOnly} 도 {@code closePosition} 도 아니다.
     */
    private static final String OPEN_ORDERS = """
            [
              {"symbol":"BTCUSDT","side":"SELL","positionSide":"BOTH","type":"STOP_MARKET",
               "stopPrice":"58000","origQty":"0","reduceOnly":false,"closePosition":true},
              {"symbol":"BTCUSDT","side":"SELL","positionSide":"BOTH","type":"TAKE_PROFIT_MARKET",
               "stopPrice":"64000","origQty":"0.05","reduceOnly":true,"closePosition":false},
              {"symbol":"BTCUSDT","side":"BUY","positionSide":"BOTH","type":"TRAILING_STOP_MARKET",
               "stopPrice":"0","origQty":"0","reduceOnly":false,"closePosition":true},
              {"symbol":"BTCUSDT","side":"BUY","positionSide":"BOTH","type":"LIMIT",
               "price":"57000","stopPrice":"0","origQty":"0.05",
               "reduceOnly":false,"closePosition":false}
            ]""";

    private FakeOpenOrderExchange exchange;

    @AfterEach
    void stop() {
        if (exchange != null) {
            exchange.close();
        }
    }

    @Override
    protected LoadOpenOrdersPort port() {
        if (exchange == null) {
            exchange = new FakeOpenOrderExchange(OPEN_ORDERS);
        }
        return new BinanceOpenOrderAdapter(
                exchange.client(),
                new BinanceCredentials("key", "secret"),
                new BinanceServerClock(exchange.client(), Clock.systemUTC()));
    }

    /**
     * <b>포지션을 여는 주문은 보호가 아니다.</b> 진입 지정가를 담으면 "손절이 걸려 있다" 는
     * 판정이 그냥 통과하고, 그것이 이 기능이 막으려는 상태 그 자체다.
     *
     * <p>인메모리 쪽은 타입상 이 실수를 할 수 없어(도메인 객체만 담는다) 계약이 아니라
     * 어댑터의 일이다. 여기서 본다.
     */
    @Test
    void 진입_지정가는_보호_주문이_아니다() {
        assertThat(port().protectiveOrdersFor(Symbol.BTC_USDT))
                .noneSatisfy(order -> assertThat(order.triggerPrice())
                        .map(price -> price.value().toPlainString()).contains("57000.00"));
    }
}
