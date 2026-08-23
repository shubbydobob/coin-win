package com.coinwin.watch.adapter.out.binance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 바이낸스 공지 API 주소.
 *
 * <p><b>{@code market} 의 것과 호스트가 다르다.</b> 시세는 {@code fapi.binance.com} 이고 공지는
 * {@code www.binance.com} 이다. 기존 클라이언트에 절대 URL 을 넣어 쓰면 {@code baseUrl} 이
 * 있으나 마나 해지고, 테스트에서 호스트를 바꿔 끼울 수 없다.
 *
 * @param baseUrl 공지 페이지 주소. 테스트는 페이크 서버 주소로 덮어쓴다
 */
@ConfigurationProperties("coinwin.watch.binance")
public record BinanceCmsProperties(String baseUrl) {
}
