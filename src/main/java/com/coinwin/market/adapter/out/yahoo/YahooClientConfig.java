package com.coinwin.market.adapter.out.yahoo;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 야후 전용 {@link RestClient}. {@code UpbitClientConfig} 와 같은 모양이다.
 *
 * <p>빈 이름을 {@code yahooRestClient} 로 갈라 둔다 — {@code RestClient} 가 이제 셋이라
 * 이름 없이 주입하면 어디로 나가는지가 배선 순서에 달린다.
 *
 * <p><b>User-Agent 를 붙인다.</b> 야후는 그것이 없으면 거절하는 경우가 있고, 문서화된 API 가
 * 아니라 그 조건이 언제 바뀌는지 알 수 없다.
 */
@Configuration
@EnableConfigurationProperties(YahooProperties.class)
public class YahooClientConfig {

    @Bean
    public RestClient yahooRestClient(YahooProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; CoinWin/1.0)")
                .requestFactory(requestFactory)
                .build();
    }
}
