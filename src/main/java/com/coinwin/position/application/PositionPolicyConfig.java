package com.coinwin.position.application;

import com.coinwin.market.application.port.in.LoadLeverageBracketsUseCase;
import com.coinwin.market.domain.Symbol;
import com.coinwin.position.domain.MaintenanceMarginPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 정책 구현체를 고르는 지점.
 *
 * <p>{@code position/domain} 에 두지 않는 이유는 ArchUnit 규칙 1 이다 — 도메인은 Spring 을
 * 모른다. 어떤 구현을 쓸지는 조립의 문제이므로 application 층에 있는 것이 맞다.
 *
 * <p>Phase 3 에서 {@code FixedMaintenanceMarginPolicy} 를 구간표 기반으로 교체했다.
 * 고정 구현은 도메인 테스트에 남아 있다 — 공식만 검사하려면 MMR 을 직접 주입해야 하기
 * 때문이다. ADR 008 의 철회 조건("Phase 3 이 끝났는데도 구현체가 하나뿐이면 인라인한다")은
 * 두 번째 구현체가 실제로 도착했으므로 발동하지 않는다.
 */
@Configuration
public class PositionPolicyConfig {

    @Bean
    public MaintenanceMarginPolicy maintenanceMarginPolicy(LoadLeverageBracketsUseCase brackets) {
        return new BracketMaintenanceMarginPolicy(brackets, Symbol.BTC_USDT);
    }
}
