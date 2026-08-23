package com.coinwin.watch.adapter.out.binance;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 공지 전용 {@link RestClient}.
 *
 * <p>이름으로 {@code market} 의 것과 가른다. 하나에 몰아넣고 절대 URL 을 쓰면 {@code baseUrl}
 * 이 무의미해진다.
 *
 * <p><b>{@code User-Agent} 를 붙인다.</b> 이 엔드포인트는 웹사이트가 쓰는 것이라 기본 자바
 * 클라이언트 표시로는 거절당할 수 있다. 실제로 호출해 확인한 조건이다.
 */
@Configuration
@EnableConfigurationProperties(BinanceCmsProperties.class)
public class BinanceCmsConfig {

    @Bean
    public RestClient binanceCmsRestClient(BinanceCmsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; CoinWin/1.0)")
                .build();
    }
}
