package com.coinwin.account.adapter.out.binance;

import com.coinwin.account.application.port.out.LoadOpenOrdersPort;
import com.coinwin.account.domain.ProtectiveOrder;
import com.coinwin.common.binance.BinanceServerClock;
import com.coinwin.common.binance.SignedBinanceApi;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.domain.Symbol;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스 {@code /fapi/v1/openOrders} 에서 걸려 있는 미체결 주문을 읽는다.
 *
 * <p><b>이 어댑터가 있어야 "손절이 걸려 있지 않다" 를 말할 수 있다.</b> 포지션만 읽으면 무엇을
 * 들고 있는지는 알아도 그것이 보호되고 있는지는 알 수 없다 —
 * {@code docs/spec/exit-automation.md} § 5 의 1단계가 이 한 번의 호출 위에 서 있다.
 *
 * <p><b>읽기만 한다.</b> 주문을 내는 엔드포인트는 이 클래스에 없다. 이것은 {@code scope.md} 의
 * 읽기 전용 키 전제 그대로이며, 거는 쪽(3단계)은 그 문서를 먼저 고친 뒤에 온다.
 */
public class BinanceOpenOrderAdapter implements LoadOpenOrdersPort {

    private static final String OPEN_ORDERS = "/fapi/v1/openOrders";

    private final SignedBinanceApi signed;

    BinanceOpenOrderAdapter(
            RestClient binanceRestClient, BinanceCredentials credentials,
            BinanceServerClock clock) {
        this.signed = new SignedBinanceApi(
                binanceRestClient, credentials.apiKey(), credentials.secretKey(), clock);
    }

    @Override
    public List<ProtectiveOrder> protectiveOrdersFor(Symbol symbol) {
        BinanceOpenOrder[] body = fetch(symbol);
        return body == null ? List.of() : Arrays.stream(body)
                .filter(BinanceOpenOrder::isProtective)
                .map(BinanceOpenOrder::toDomain)
                .toList();
    }

    /** 질의 문자열을 메시지에 넣지 않는다. 계좌를 특정할 수 있는 값이 섞인다. */
    private BinanceOpenOrder[] fetch(Symbol symbol) {
        try {
            return signed.get(OPEN_ORDERS, "symbol=" + symbol.value(), BinanceOpenOrder[].class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스에서 미체결 주문을 가져오지 못했다: " + symbol.value(), e);
        }
    }
}
