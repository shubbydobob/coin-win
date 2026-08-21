package com.coinwin.position.application;

import com.coinwin.position.domain.FixedMaintenanceMarginPolicy;
import com.coinwin.position.domain.MaintenanceMarginPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 정책 구현체를 고르는 지점.
 *
 * <p>{@code position/domain} 에 두지 않는 이유는 ArchUnit 규칙 1 이다 — 도메인은 Spring 을
 * 모른다. 어떤 구현을 쓸지는 조립의 문제이므로 application 층에 있는 것이 맞다.
 */
@Configuration
public class PositionPolicyConfig {

    /** Phase 3 에서 {@code leverageBracket} 기반 구현으로 교체된다. 근거는 ADR 008. */
    @Bean
    public MaintenanceMarginPolicy maintenanceMarginPolicy() {
        return FixedMaintenanceMarginPolicy.btcUsdtApproximation();
    }
}
