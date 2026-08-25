package com.coinwin.market.adapter.out.binance;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 바이낸스 공개 엔드포인트 접속 설정.
 *
 * <p>API 키가 없다. 시세·펀딩비·OI·롱숏비율·레버리지 구간은 전부 공개 엔드포인트라 서명이
 * 필요 없다. 근거: {@code .claude/docs/scope.md} — 키가 필요한 기능은 이 프로젝트 범위 밖이다.
 *
 * @param baseUrl 거래소 주소. 테스트는 페이크 서버 주소로 덮어쓴다.
 * @param spotBaseUrl 현물 시장 주소. <b>무기한과 호스트가 다르다</b>({@code fapi} 대
 *     {@code api}). 비트코인 현물가 하나 때문에 생겼고, 그 값이 무기한가와 갈라지는 것이
 *     관측값이므로 같은 주소에서 읽을 수는 없다.
 * @param maxCandlesPerRequest 한 번에 받을 캔들 수. 바이낸스 상한은 1500 이고, 그보다 크게
 *     요청하면 조용히 잘린 응답이 온다. 잘린 줄 모르면 캔들에 구멍이 생긴다.
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 대기 제한 시간
 */
@ConfigurationProperties("coinwin.market.binance")
public record BinanceProperties(
        String baseUrl,
        String spotBaseUrl,
        int maxCandlesPerRequest,
        Duration connectTimeout,
        Duration readTimeout) {
}
