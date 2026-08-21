package com.coinwin.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 설정.
 *
 * <p>config 는 domain 이 아니므로 Spring / Swagger 의존이 허용된다.
 * ArchUnit 규칙 1 은 {@code ..domain..} 패키지에만 적용된다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coinWinOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CoinWin API")
                .version("0.0.1")
                .description("""
                        비트코인 선물 매매 보조 도구.
                        분할 진입 시 총 리스크를 진입 전에 산출하고, 매매 이력을 구조화해 기록한다.
                        주문 실행과 매매 시점 추천은 범위 밖이다."""));
    }
}
