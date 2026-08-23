package com.coinwin.market.adapter.out.upbit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업비트 공개 시세 접속 설정. 키가 없다 — 시세 조회는 서명이 필요 없다.
 *
 * <p>바이낸스와 따로 두는 이유는 <b>다른 거래소이기 때문</b>이다. 한 블록에 섞으면
 * "이 주소가 어디 것인가" 가 설정에서 사라지고, 한쪽이 막혔을 때 무엇을 바꿔야 하는지도
 * 흐려진다. {@code watch.binance} 를 따로 둔 것과 같은 판단이다.
 *
 * @param baseUrl 업비트 주소. 테스트는 페이크 서버 주소로 덮어쓴다.
 * @param market 시세를 읽을 시장. 원화 마켓의 USDT 다.
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 대기 제한 시간
 */
@ConfigurationProperties("coinwin.market.upbit")
public record UpbitProperties(
        String baseUrl,
        String market,
        Duration connectTimeout,
        Duration readTimeout) {
}
