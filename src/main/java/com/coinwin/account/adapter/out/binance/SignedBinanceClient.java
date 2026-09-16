package com.coinwin.account.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 서명이 필요한 바이낸스 GET 하나.
 *
 * <p><b>두 번째 서명 어댑터가 생기면서 추출했다.</b> 포지션과 미체결 주문이 같은 질의 문자열
 * 규칙·같은 시계·같은 오류 변환을 쓴다. 갈라 두면 한쪽만 고치는 날이 오고, 그 어긋남은
 * 401 로만 드러난다({@code .claude/docs/conventions.md} 의 중복 규칙).
 *
 * <p><b>질의 문자열을 손으로 만든다.</b> 서명 대상이 보내는 문자열과 한 글자라도 다르면 401 만
 * 오고 거래소는 무엇이 틀렸는지 말해 주지 않는다. {@code UriBuilder} 에 맡기면 인코딩과
 * 파라미터 순서를 프레임워크가 정하게 되고, 그 결정이 바뀌는 것을 우리가 알 수 없다.
 *
 * <p><b>시각은 거래소 시계에서 온다</b>({@link BinanceServerClock}). 서명 타임스탬프의 신선도를
 * 판정하는 것이 거래소이므로 우리 시계를 쓰면 기계가 몇 초만 어긋나도 전부 거절된다.
 *
 * <p><b>읽기만 한다.</b> {@code POST} 를 두지 않는다 — 주문을 내는 것은
 * {@code docs/spec/exit-automation.md} § 5 의 3단계이고 그때 {@code scope.md} 를 먼저 고친다.
 */
final class SignedBinanceClient {

    /** 요청이 거래소에 늦게 닿았을 때 거절할 여유. 바이낸스 기본값과 같다. */
    private static final long RECV_WINDOW_MILLIS = 5_000;

    private final RestClient client;
    private final BinanceSigner signer;
    private final String apiKey;
    private final BinanceServerClock clock;

    SignedBinanceClient(
            RestClient client, BinanceCredentials credentials, BinanceServerClock clock) {
        this.client = client;
        this.signer = new BinanceSigner(credentials.secretKey());
        this.apiKey = credentials.apiKey();
        this.clock = clock;
    }

    /** 서명 타임스탬프이자 관측 시각. 둘을 섞으면 "언제 본 값인가" 가 서명과 어긋난다. */
    Instant now() {
        return clock.now();
    }

    /**
     * 종목 하나를 묻는 서명 GET.
     *
     * <p>인자를 {@link Request} 로 묶은 이유는 Checkstyle 의 파라미터 한계(4) 때문만이 아니다 —
     * 네 값이 <b>함께 다녀야</b> 서명과 관측 시각이 어긋나지 않는다.
     */
    <T> T get(Request request, Class<T> type) {
        String query = "symbol=%s&recvWindow=%d&timestamp=%d".formatted(
                request.symbol().value(), RECV_WINDOW_MILLIS, request.at().toEpochMilli());
        try {
            return client.get()
                    .uri(request.path() + "?" + query + "&signature=" + signer.sign(query))
                    .header("X-MBX-APIKEY", apiKey)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스에서 %s 를 가져오지 못했다: %s"
                            .formatted(request.what(), request.symbol().value()), e);
        }
    }

    /**
     * 서명 GET 한 번의 전부.
     *
     * @param what 실패 메시지에 적을 대상. <b>질의 문자열은 넣지 않는다</b> — 계좌를 특정할 수
     *     있는 값이 섞이고 로그는 그보다 훨씬 넓게 흐른다
     */
    record Request(String path, Symbol symbol, Instant at, String what) {
    }
}
