package com.coinwin.market.adapter.out.yahoo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 야후 설정. <b>키가 없다</b> — 이것 하나는 다행이라 서명 경로가 늘지 않는다.
 *
 * <p>제한 시간을 명시하는 이유는 다른 클라이언트와 같다. 기본값이 무한이라 응답하지 않는
 * 상대가 요청 스레드를 영원히 잡는다.
 */
@ConfigurationProperties("coinwin.market.yahoo")
public record YahooProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
