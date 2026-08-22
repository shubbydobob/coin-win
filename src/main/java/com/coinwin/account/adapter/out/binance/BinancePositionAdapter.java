package com.coinwin.account.adapter.out.binance;

import com.coinwin.account.application.port.out.LoadExchangePositionsPort;
import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스 {@code /fapi/v3/positionRisk} 에서 열려 있는 포지션을 읽는다.
 *
 * <p>이 프로젝트에서 <b>서명이 필요한 첫 어댑터</b>다. Phase 3 은 레버리지 구간표가 401 을
 * 내자 서명을 붙이는 대신 커밋된 스냅샷으로 우회했다 — 그때는 그것이 옳았고(구간표는 거의
 * 변하지 않는다), 포지션은 매 순간 달라지므로 그 우회가 성립하지 않는다.
 *
 * <p><b>읽기만 한다.</b> 주문을 내는 엔드포인트는 이 클래스에 없고 앞으로도 없다 —
 * {@code CLAUDE.md} 의 금지 항목이다. 키도 읽기 전용으로 발급해야 한다({@code scope.md}).
 *
 * <p><b>시각은 거래소 시계에서 온다</b>({@link BinanceServerClock}). 서명 타임스탬프의 신선도를
 * 판정하는 것이 거래소이므로 우리 시계를 쓰면 기계가 몇 초만 어긋나도 전부 거절된다.
 * 관측 시각도 같은 값을 쓴다 — 두 시계를 섞으면 "언제 본 값인가" 가 서명과 어긋난다.
 *
 * <p><b>수량 0 은 포지션이 아니다.</b> 거래소는 한 번이라도 연 적 있는 종목을 전부 돌려주고
 * 닫힌 것은 {@code positionAmt: "0"} 이다. 그것을 담으면 "포지션 있음" 이 되어 대조가 통째로
 * 뒤집힌다. 포트의 계약이기도 하다.
 */
public class BinancePositionAdapter implements LoadExchangePositionsPort {

    private static final String POSITION_RISK = "/fapi/v3/positionRisk";

    /** 요청이 거래소에 늦게 닿았을 때 거절할 여유. 바이낸스 기본값과 같다. */
    private static final long RECV_WINDOW_MILLIS = 5_000;

    private final RestClient client;
    private final BinanceSigner signer;
    private final String apiKey;
    private final BinanceServerClock clock;

    BinancePositionAdapter(
            RestClient binanceRestClient, BinanceCredentials credentials,
            BinanceServerClock clock) {
        this.client = binanceRestClient;
        this.signer = new BinanceSigner(credentials.secretKey());
        this.apiKey = credentials.apiKey();
        this.clock = clock;
    }

    @Override
    public List<ExchangePosition> positionsFor(Symbol symbol) {
        Instant observedAt = clock.now();
        return Arrays.stream(fetch(symbol, observedAt))
                .filter(BinancePositionRisk::isOpen)
                .map(risk -> risk.toDomain(observedAt))
                .toList();
    }

    /**
     * 질의 문자열을 <b>손으로 만든다.</b> 서명 대상이 보내는 문자열과 한 글자라도 다르면
     * 401 만 오고 거래소는 무엇이 틀렸는지 말해 주지 않는다. {@code UriBuilder} 에 맡기면
     * 인코딩과 파라미터 순서를 프레임워크가 정하게 되고, 그 결정이 바뀌는 것을 우리가 알 수 없다.
     */
    private BinancePositionRisk[] fetch(Symbol symbol, Instant at) {
        String query = "symbol=%s&recvWindow=%d&timestamp=%d"
                .formatted(symbol.value(), RECV_WINDOW_MILLIS, at.toEpochMilli());
        try {
            BinancePositionRisk[] body = client.get()
                    .uri(POSITION_RISK + "?" + query + "&signature=" + signer.sign(query))
                    .header("X-MBX-APIKEY", apiKey)
                    .retrieve()
                    .body(BinancePositionRisk[].class);
            return body == null ? new BinancePositionRisk[0] : body;
        } catch (RestClientException e) {
            // 질의 문자열을 메시지에 넣지 않는다. 계좌를 특정할 수 있는 값이 섞인다.
            throw new ExternalDataUnavailableException(
                    "바이낸스에서 포지션을 가져오지 못했다: " + symbol.value(), e);
        }
    }
}
