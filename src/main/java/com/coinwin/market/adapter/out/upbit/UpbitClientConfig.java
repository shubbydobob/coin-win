package com.coinwin.market.adapter.out.upbit;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 업비트 전용 {@link RestClient}.
 *
 * <p>{@code BinanceClientConfig} 와 같은 모양이고 같은 이유로 제한 시간을 명시한다 —
 * 기본값이 무한이라, 응답하지 않는 거래소가 요청 스레드를 영원히 잡는다.
 *
 * <p>빈 이름을 {@code upbitRestClient} 로 갈라 둔다. {@code RestClient} 가 둘이 되었으므로
 * 이름 없이 주입하면 어느 거래소로 나가는지가 배선 순서에 달리게 된다.
 */
@Configuration
@EnableConfigurationProperties(UpbitProperties.class)
public class UpbitClientConfig {

    @Bean
    public RestClient upbitRestClient(UpbitProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
