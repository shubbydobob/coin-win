package com.coinwin.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

    private static final String RESPONSE_SUFFIX = "Response";

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

    /**
     * 응답 스키마의 모든 프로퍼티를 {@code required} 로 세운다.
     *
     * <p>springdoc 은 {@code required} 를 내지 않는다. 그대로 두면 소비자 쪽 생성 타입에서
     * 모든 필드가 optional 이 되고, <b>항상 있는 값과 진짜 null 인 값이 구별되지 않는다.</b>
     * "손익비가 0" 과 "손익비를 말할 수 없다" 가 같은 모양이 되는 것이 그 예다.
     *
     * <p>DTO 40여 개에 애너테이션을 흩뿌리는 대신 여기 한 곳에 둔다. 필드마다 붙이는 것은
     * 규칙이 아니라 습관이고, 습관은 새 DTO 에서 빠진다.
     *
     * <p><b>요청 스키마는 건드리지 않는다.</b> 요청은 실제로 선택 필드가 있고, 무엇이 필수인지의
     * 검증은 어차피 서버가 한다. 이름이 {@code Response} 로 끝나는 것만 대상으로 삼는 것은
     * 현재 42개 스키마가 요청/응답으로 정확히 갈라져 있기 때문이며, 그 전제는
     * {@code ResponseSchemaContractTest} 가 매 빌드 검사한다.
     *
     * <p>이것은 프론트를 위한 편의가 아니라 <b>계약의 수정</b>이다. 지금 Swagger 는 손익비가
     * 언제나 숫자라고 말하고 있고, 그것은 어느 소비자에게나 거짓이다.
     *
     * <p>근거: {@code docs/spec/phase8-frontend.md} § 5.4
     */
    @Bean
    public OpenApiCustomizer responseFieldsAreRequired() {
        return openApi -> openApi.getComponents().getSchemas()
                .forEach(OpenApiConfig::markPropertiesRequired);
    }

    private static void markPropertiesRequired(String name, Schema<?> schema) {
        if (!name.endsWith(RESPONSE_SUFFIX) || schema.getProperties() == null) {
            return;
        }
        schema.getProperties().keySet().forEach(schema::addRequiredItem);
    }
}
