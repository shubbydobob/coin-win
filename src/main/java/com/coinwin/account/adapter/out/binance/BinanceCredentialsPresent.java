package com.coinwin.account.adapter.out.binance;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 두 키가 <b>모두 비어 있지 않을 때만</b> 참.
 *
 * <p>{@code @ConditionalOnProperty} 를 쓰지 않는 이유는 그것이 <b>빈 문자열을 "있음" 으로</b>
 * 보기 때문이다. 환경변수를 설정하지 않은 채 {@code application.yml} 에 {@code ${VAR:}} 로
 * 기본값을 두면 값은 빈 문자열이 되고, 조건은 통과하고, 어댑터가 빈 시크릿으로 서명하려다
 * <b>기동 중에</b> 터진다. 그러면 키와 무관한 화면까지 전부 못 쓰게 된다 — Phase 7 이 AI 에서
 * 겪은 것과 같은 고장이다.
 *
 * <p>한쪽만 있는 경우도 거짓이다. API 키만 있고 시크릿이 없으면 서명할 수 없고, 그 상태로
 * 부르면 401 이 오는데 그 401 의 원인이 "키가 없다" 인지 "키가 틀렸다" 인지 구분되지 않는다.
 */
public class BinanceCredentialsPresent implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(
                        context.getEnvironment().getProperty("coinwin.account.binance.api-key"))
                && StringUtils.hasText(
                        context.getEnvironment().getProperty("coinwin.account.binance.secret-key"));
    }
}
