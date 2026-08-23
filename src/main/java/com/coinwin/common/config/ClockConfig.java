package com.coinwin.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * "지금" 을 읽는 시계. 애플리케이션에 하나뿐이다.
 *
 * <p>{@code Instant.now()} 를 서비스와 어댑터가 직접 부르지 않게 하려는 것이 전부다. 직접
 * 부르면 시각을 고정할 수 없어 "계획 시각이 언제로 남는가" 를 테스트가 단언하지 못하고,
 * 체결이 계획보다 앞설 수 없다는 규칙도 검사할 수 없다.
 *
 * <p>UTC 인 이유는 저장이 {@code TIMESTAMPTZ} 이고 거래소 시각도 UTC 이기 때문이다. 시스템
 * 기본 시간대를 쓰면 개발 기계와 서버에서 다른 값이 찍힌다.
 *
 * <p><b>{@code journal} 에서 {@code common} 으로 옮겼다.</b> 처음에는 계획 시각을 찍는 용도라
 * {@code journal.application} 에 있었는데, {@code market} 의 호가 어댑터가 같은 시계를 필요로
 * 하면서 <b>모듈 간 숨은 결합</b>이 됐다 — 코드가 서로를 import 하지 않으니 ArchUnit 도 잡지
 * 못하고, {@code journal} 의 설정이 움직이면 {@code market} 이 뜨지 않는다. 모든 모듈이
 * {@code common} 을 보는 것은 아키텍처가 이미 허용한 방향이다.
 *
 * <p>거래소 시각은 이 시계로 대신하지 않는다. 서명 타임스탬프는 {@code BinanceServerClock} 이
 * 따로 재고, 호가·체결 시각은 거래소가 준 값을 그대로 쓴다. 이 시계가 쓰이는 자리는 <b>거래소가
 * 시각을 주지 않을 때</b> 뿐이다.
 */
@Configuration
public class ClockConfig {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
