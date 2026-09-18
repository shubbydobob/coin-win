package com.coinwin.trading.adapter.out.binance;

import com.coinwin.common.binance.BinanceServerClock;
import com.coinwin.common.binance.SignedBinanceApi;
import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import com.coinwin.trading.domain.OrderId;
import com.coinwin.trading.domain.OrderIntent;
import com.coinwin.trading.domain.OrderKind;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.TradingMode;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스에 <b>실제로 주문을 낸다.</b> 이 프로젝트에서 돈이 움직일 수 있는 유일한 클래스다.
 *
 * <p><b>출금 엔드포인트가 없고 앞으로도 없다.</b> {@code scope.md} 가 주문 실행만 조건부로
 * 해제했다. 키에서도 끈다 — 코드와 권한 양쪽에서 막는 것은 한쪽이 무너져도 다른 쪽이 남기
 * 위해서다.
 *
 * <p><b>모드를 생성자가 받는다.</b> 테스트넷과 실계좌는 주소만 다르고 코드는 같은데, 그
 * 사실이 위험하다 — 어느 쪽에 붙어 있는지 객체가 모르면 기록에도 안 남는다. 그래서 주소와
 * 모드를 함께 받고, <b>모든 {@link PlacedOrder} 에 모드가 실린다.</b>
 *
 * <p><b>포지션을 뒤집을 수 없다.</b> 줄이는 주문에는 {@code reduceOnly} 나
 * {@code closePosition} 이 붙는다 — 수량 계산이 틀려도 반대 포지션이 열리지 않는다는 뜻이고,
 * 사람이 보고 있지 않은 동안 도는 루프에서 그 보장은 값이 크다.
 *
 * <p><b>안전장치를 여기서 하지 않는다.</b> {@code RiskLimits} 가 이미 판정했고, 어댑터가 또
 * 검사하면 구현체마다 다른 한계를 갖게 된다.
 */
public final class BinanceOrderAdapter implements PlaceOrderPort {

    private static final String ORDER = "/fapi/v1/order";

    private final SignedBinanceApi signed;
    private final TradingMode mode;

    public BinanceOrderAdapter(
            RestClient client, BinanceOrderCredentials credentials,
            BinanceServerClock clock, TradingMode mode) {
        assertNotPaper(DomainValues.required(mode, "모드"));
        this.signed = new SignedBinanceApi(
                client, credentials.apiKey(), credentials.secretKey(), clock);
        this.mode = mode;
    }

    /** <b>먼저 본다.</b> 필드를 채운 뒤 던지면 반쯤 만들어진 객체가 남는다. */
    private static void assertNotPaper(TradingMode mode) {
        if (mode == TradingMode.PAPER) {
            throw new IllegalArgumentException(
                    "장부 모드는 거래소 어댑터가 아니다 — 그 자리는 PaperBrokerAdapter 다");
        }
    }

    @Override
    public TradingMode mode() {
        return mode;
    }

    @Override
    public PlacedOrder place(OrderIntent intent) {
        DomainValues.required(intent, "주문 의도");
        BinanceOrderResponse response = send(intent);
        return new PlacedOrder(
                new OrderId(String.valueOf(response.orderId())),
                intent,
                intent.kind().needsTrigger() ? Optional.empty() : filledAt(response, intent),
                signed.now(),
                mode);
    }

    /**
     * 없는 주문을 취소하는 것은 실패가 아니다 — 이미 체결됐거나 이미 취소된 것이고, 어느
     * 쪽이든 원하던 상태(그 주문이 없음)가 됐다. 거래소는 그때 {@code -2011} 을 준다.
     */
    @Override
    public void cancel(OrderId id) {
        DomainValues.required(id, "주문 식별자");
        try {
            signed.delete(ORDER, "symbol=BTCUSDT&orderId=" + id.value(), String.class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException("주문을 취소하지 못했다: " + id.value(), e);
        }
    }

    private BinanceOrderResponse send(OrderIntent intent) {
        try {
            return signed.post(ORDER, BinanceOrderRequest.of(intent).query(),
                    BinanceOrderResponse.class);
        } catch (RestClientException e) {
            // 질의 문자열을 메시지에 넣지 않는다. 계좌를 특정할 수 있는 값이 섞인다.
            throw new ExternalDataUnavailableException(
                    "%s %s 주문을 내지 못했다".formatted(intent.position(), intent.kind()), e);
        }
    }

    /**
     * 시장가인데 체결가가 안 왔으면 기준가로 적지 않는다.
     *
     * <p>지어내면 손익이 그만큼 틀리고, 그 오차는 <b>언제나 우리에게 유리한 쪽</b>으로
     * 기운다(기준가는 미끄러지기 전 값이다). 그럴 때는 거래소가 준 것이 없다는 사실을
     * 그대로 둔다 — 다만 {@code PlacedOrder} 가 시장가에 체결가를 요구하므로 던진다.
     */
    private static Optional<Price> filledAt(BinanceOrderResponse response, OrderIntent intent) {
        return Optional.of(response.filledAt().map(Price::of).orElseThrow(() ->
                new ExternalDataUnavailableException(
                        "%s 시장가 주문의 체결가를 거래소가 말하지 않았다".formatted(intent.kind()))));
    }

    /** 주문 종류가 거래소의 말로 무엇인가. */
    static String typeOf(OrderKind kind) {
        return switch (kind) {
            case ENTRY, EXIT -> "MARKET";
            case STOP_LOSS -> "STOP_MARKET";
            case TAKE_PROFIT -> "TAKE_PROFIT_MARKET";
        };
    }

    /** 롱을 닫는 것은 매도, 숏을 닫는 것은 매수. 여는 쪽은 그 반대다. */
    static String sideOf(OrderIntent intent) {
        boolean sells = intent.kind().reducesPosition() == (intent.position() == Direction.LONG);
        return sells ? "SELL" : "BUY";
    }
}
