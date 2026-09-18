package com.coinwin.common.binance;

import org.springframework.web.client.RestClientException;

/**
 * 바이낸스를 읽지 못했다.
 *
 * <p><b>{@code RestClientException} 을 상속하는 것이 이 클래스의 요점이다.</b> 어댑터들은
 * 이미 그 타입을 잡아 자기 모듈의 말로 바꾸고 있으므로, 이 예외도 같은 자리에서 잡힌다 —
 * 새 {@code catch} 를 모든 어댑터에 더하는 것보다 낫고, 빠뜨릴 자리도 없다.
 *
 * <p><b>도메인 예외를 쓰지 않는 이유는 계층 규칙이다.</b> 이 패키지는 네 층(api · adapter ·
 * application · domain) 중 어디에도 속하지 않으므로 {@code common.domain} 을 참조할 수 없다.
 * 규칙이 옳다 — 전송 계층이 도메인의 말을 하기 시작하면 어느 모듈에도 속하지 않은 코드가
 * 어휘를 갖게 된다. 도메인의 말로 바꾸는 일은 어댑터가 한다.
 */
public class BinanceUnavailableException extends RestClientException {

    private static final long serialVersionUID = 1L;

    public BinanceUnavailableException(String message) {
        super(message);
    }

    public BinanceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
