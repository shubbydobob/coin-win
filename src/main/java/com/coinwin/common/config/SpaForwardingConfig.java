package com.coinwin.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 새로고침해도 화면이 남아 있게 한다.
 *
 * <p>{@code /journal} 을 새로고침하면 브라우저가 그 경로를 <b>서버에</b> 묻는다. 라우팅은
 * 자바스크립트가 하는 것이므로 서버에는 그런 경로가 없고, 아무 처리도 하지 않으면 404 다.
 * 그래서 {@code index.html} 로 포워드한다.
 *
 * <p><b>삼키지 않는 것이 이 클래스의 어려운 부분이다.</b> 패턴이 한 칸짜리 경로만 잡으므로
 * {@code /api/trades} · {@code /v3/api-docs} · {@code /swagger-ui/index.html} ·
 * {@code /assets/index-x.js} 는 애초에 걸리지 않는다. 점이 있는 이름({@code [^.]})과 예약된
 * 첫 칸을 한 번 더 제외하는 것은 {@code /api} 처럼 <b>한 칸</b>으로 들어오는 요청까지 막기
 * 위해서다 — 그런 요청에 화면을 돌려주면 API 오류가 200 짜리 HTML 로 보인다.
 *
 * <p>특히 Swagger UI 는 Phase 0 부터 있던 것이고, 프론트가 그것을 가리면 <b>API 문서가 조용히
 * 사라진다.</b> 무엇이 안 삼켜지는지는 {@code SpaForwardingTest} 가 전부 확인한다.
 *
 * <p>이 클래스는 {@code domain} 이 아니므로 ArchUnit 규칙 1 에 걸리지 않는다.
 * 근거: {@code docs/spec/phase8-frontend.md} § 9.4
 */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {

    /** 한 칸짜리 경로 중 예약어가 아니고 점이 없는 것. 화면 라우트가 전부 이 모양이다. */
    private static final String SPA_ROUTE = "/{path:^(?!api|v3|swagger-ui|assets|actuator)[^.]*$}";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController(SPA_ROUTE).setViewName("forward:/index.html");
    }
}
