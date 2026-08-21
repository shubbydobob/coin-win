package com.coinwin.journal.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 계획 시각을 찍는 시계.
 *
 * <p>{@code Instant.now()} 를 서비스가 직접 부르지 않게 하려는 것이 전부다. 그러면 계획 시각을
 * 고정할 수 없어 "계획 시각이 언제로 남는가" 를 테스트가 단언하지 못하고, 체결이 계획보다
 * 앞설 수 없다는 규칙도 검사할 수 없다.
 *
 * <p>UTC 인 이유는 저장이 {@code TIMESTAMPTZ} 이고 거래소 시각도 UTC 이기 때문이다.
 * 시스템 기본 시간대를 쓰면 개발 기계와 서버에서 다른 값이 찍힌다.
 */
@Configuration
public class JournalClockConfig {

    @Bean
    @Primary
    public Clock clock() {
        return Clock.systemUTC();
    }
}
