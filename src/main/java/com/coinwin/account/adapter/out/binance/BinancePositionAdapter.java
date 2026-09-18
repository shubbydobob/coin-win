package com.coinwin.account.adapter.out.binance;

import com.coinwin.account.application.port.out.LoadExchangePositionsPort;
import com.coinwin.account.domain.ExchangePosition;
import com.coinwin.common.binance.BinanceServerClock;
import com.coinwin.common.binance.SignedBinanceApi;
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
 * <p><b>서명·시계·오류 변환은 {@link SignedBinanceClient} 가 한다.</b> 미체결 주문 어댑터가
 * 같은 규칙을 쓰므로 두 번째가 생긴 자리에서 추출했다. 관측 시각도 그 시계에서 온다 —
 * 두 시계를 섞으면 "언제 본 값인가" 가 서명과 어긋난다.
 *
 * <p><b>수량 0 은 포지션이 아니다.</b> 거래소는 한 번이라도 연 적 있는 종목을 전부 돌려주고
 * 닫힌 것은 {@code positionAmt: "0"} 이다. 그것을 담으면 "포지션 있음" 이 되어 대조가 통째로
 * 뒤집힌다. 포트의 계약이기도 하다.
 */
public class BinancePositionAdapter implements LoadExchangePositionsPort {

    private static final String POSITION_RISK = "/fapi/v3/positionRisk";

    private final SignedBinanceApi signed;

    BinancePositionAdapter(
            RestClient binanceRestClient, BinanceCredentials credentials,
            BinanceServerClock clock) {
        this.signed = new SignedBinanceApi(
                binanceRestClient, credentials.apiKey(), credentials.secretKey(), clock);
    }

    @Override
    public List<ExchangePosition> positionsFor(Symbol symbol) {
        Instant observedAt = signed.now();
        BinancePositionRisk[] body = fetch(symbol);
        return body == null ? List.of() : Arrays.stream(body)
                .filter(BinancePositionRisk::isOpen)
                .map(risk -> risk.toDomain(observedAt))
                .toList();
    }

    /** 질의 문자열을 메시지에 넣지 않는다. 계좌를 특정할 수 있는 값이 섞인다. */
    private BinancePositionRisk[] fetch(Symbol symbol) {
        try {
            return signed.get(POSITION_RISK, "symbol=" + symbol.value(),
                    BinancePositionRisk[].class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스에서 포지션을 가져오지 못했다: " + symbol.value(), e);
        }
    }
}
