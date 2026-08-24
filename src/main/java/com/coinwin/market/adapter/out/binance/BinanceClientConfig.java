package com.coinwin.market.adapter.out.binance;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 바이낸스 전용 {@link RestClient}.
 *
 * <p>제한 시간을 명시하는 이유는 기본값이 <b>무한</b>이기 때문이다. 거래소가 응답하지 않는
 * 동안 요청 스레드가 영원히 잡혀 있으면, 네트워크 장애가 애플리케이션 전체의 장애가 된다.
 *
 * <p>이 설정이 {@code adapter.out.binance} 안에 있는 이유는 HTTP 가 어댑터의 사정이기
 * 때문이다. {@code application} 은 이 클래스의 존재를 모른다(ArchUnit 규칙 4).
 */
@Configuration
@EnableConfigurationProperties(BinanceProperties.class)
public class BinanceClientConfig {

    @Bean
    public RestClient binanceRestClient(BinanceProperties properties) {
        return build(properties, properties.baseUrl());
    }

    /**
     * 현물 시장 전용. <b>무기한과 호스트가 달라서</b> 빈이 둘이다 — 하나로 합치려면 부르는
     * 쪽이 절대 URL 을 알아야 하고, 그러면 주소가 설정에서 코드로 새어 나온다.
     */
    @Bean
    public RestClient binanceSpotRestClient(BinanceProperties properties) {
        return build(properties, properties.spotBaseUrl());
    }

    private static RestClient build(BinanceProperties properties, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
