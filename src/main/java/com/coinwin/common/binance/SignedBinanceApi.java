package com.coinwin.common.binance;

import java.time.Instant;
import org.springframework.web.client.RestClient;

/**
 * 서명이 필요한 바이낸스 호출 하나.
 *
 * <p><b>세 번째로 필요해져서 뽑았다</b> — 포지션 · 미체결 주문 · 주문 실행이 같은 질의 문자열
 * 규칙, 같은 시계, 같은 헤더를 쓴다. 갈라 두면 한쪽만 고치는 날이 오고 그 어긋남은
 * <b>401 로만 드러난다</b>({@code .claude/docs/conventions.md} 의 중복 규칙).
 *
 * <p><b>도메인 타입을 하나도 쓰지 않는다.</b> 받는 것도 돌려주는 것도 문자열과 클래스뿐이다.
 * 계층 규칙이 그것을 요구한다 — 이 패키지는 네 층(api · adapter · application · domain) 중
 * 어디에도 속하지 않으므로 도메인을 참조할 수 없다. 그 제약이 더 나은 모양을 만들었다:
 * 이 클래스는 <b>어느 모듈의 어휘도 모르는 순수한 전송 계층</b>이 됐다.
 *
 * <p><b>질의 문자열을 손으로 받는다.</b> 서명 대상이 보내는 문자열과 한 글자라도 다르면 401 만
 * 오고 거래소는 무엇이 틀렸는지 말해 주지 않는다. {@code UriBuilder} 에 맡기면 인코딩과
 * 파라미터 순서를 프레임워크가 정하게 되고, 그 결정이 바뀌는 것을 우리가 알 수 없다.
 *
 * <p><b>예외를 감싸지 않는다.</b> {@code RestClientException} 이 그대로 올라가고 부르는 쪽이
 * 자기 모듈의 말로 바꾼다 — 여기서 메시지를 지으면 질의 문자열이 섞여 나갈 위험이 생기고,
 * 거기에는 계좌를 특정할 수 있는 값이 있다.
 */
public final class SignedBinanceApi {

    /** 요청이 거래소에 늦게 닿았을 때 거절할 여유. 바이낸스 기본값과 같다. */
    private static final long RECV_WINDOW_MILLIS = 5_000;

    private final RestClient client;
    private final BinanceSigner signer;
    private final String apiKey;
    private final BinanceServerClock clock;

    public SignedBinanceApi(
            RestClient client, String apiKey, String secretKey, BinanceServerClock clock) {
        this.client = client;
        this.signer = new BinanceSigner(secretKey);
        this.apiKey = apiKey;
        this.clock = clock;
    }

    /**
     * 서명 타임스탬프이자 관측 시각.
     *
     * <p><b>거래소 시계를 쓴다.</b> 요청의 신선도를 판정하는 것이 거래소이므로 우리 시계를
     * 쓰면 기계가 몇 초만 어긋나도 모든 요청이 {@code -1021} 로 거절된다. 실제로 그렇게 됐다.
     */
    public Instant now() {
        return clock.now();
    }

    /** 서명 GET. {@code params} 에 {@code timestamp} 와 서명을 붙여 보낸다. */
    public <T> T get(String path, String params, Class<T> type) {
        return client.get().uri(signed(path, params)).header("X-MBX-APIKEY", apiKey)
                .retrieve().body(type);
    }

    /**
     * 서명 POST — <b>주문을 내는 자리</b>.
     *
     * <p>바이낸스 선물은 본문이 아니라 질의 문자열로 받는다. 본문에 담으면 서명 대상과
     * 보내는 것이 갈라진다.
     */
    public <T> T post(String path, String params, Class<T> type) {
        return client.post().uri(signed(path, params)).header("X-MBX-APIKEY", apiKey)
                .retrieve().body(type);
    }

    /** 서명 DELETE — 걸려 있는 주문을 취소한다. */
    public <T> T delete(String path, String params, Class<T> type) {
        return client.delete().uri(signed(path, params)).header("X-MBX-APIKEY", apiKey)
                .retrieve().body(type);
    }

    private String signed(String path, String params) {
        String query = "%s&recvWindow=%d&timestamp=%d"
                .formatted(params, RECV_WINDOW_MILLIS, now().toEpochMilli());
        return path + "?" + query + "&signature=" + signer.sign(query);
    }
}
