package com.coinwin.account.adapter.out.binance;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 키가 있을 때만 거래소 포지션 어댑터를 올린다.
 *
 * <p>Phase 7 의 AI 와 같은 형태다 — <b>키가 없으면 그 자리만 비활성이고 앱은 그대로 뜬다.</b>
 * 다른 점은 여기서는 우리 빈이라 환경 후처리기까지 갈 필요가 없다는 것뿐이다.
 *
 * <p><b>폴백을 두지 않는다.</b> 키가 없을 때 인메모리 어댑터를 대신 올리면 "거래소에 포지션이
 * 없다" 는 <b>거짓말이 화면에 뜬다.</b> 비어 있는 것과 알 수 없는 것은 다른 사실이고, 대조는
 * 정확히 그 구분을 위해 있는 기능이다. 없으면 없는 채로 두고 화면이 "연결되지 않음" 을 말한다.
 */
@Configuration
@EnableConfigurationProperties(BinanceCredentials.class)
public class BinanceAccountConfig {

    /**
     * 서명 타임스탬프와 관측 시각의 출처.
     *
     * <p><b>거래소 시계에 맞춘다.</b> 요청의 신선도를 판정하는 것이 거래소이므로 우리 시계를
     * 쓰면 기계가 몇 초만 어긋나도 모든 요청이 {@code -1021} 로 거절된다. 실제로 그렇게 됐다.
     *
     * <p>키가 없어도 이 빈은 만든다. 공개 엔드포인트만 쓰고, 조건을 하나 더 걸면 "왜 이 빈이
     * 없는가" 를 두 곳에서 확인해야 한다.
     */
    @Bean
    BinanceServerClock binanceServerClock(RestClient binanceRestClient) {
        return new BinanceServerClock(binanceRestClient, Clock.systemUTC());
    }

    @Bean
    @Conditional(BinanceCredentialsPresent.class)
    BinancePositionAdapter binancePositionAdapter(
            RestClient binanceRestClient, BinanceCredentials credentials,
            BinanceServerClock binanceServerClock) {
        return new BinancePositionAdapter(binanceRestClient, credentials, binanceServerClock);
    }
}
