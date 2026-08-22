package com.coinwin.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 화면 경로는 {@code index.html} 로 포워드되고, <b>나머지는 하나도 삼켜지지 않는다.</b>
 *
 * <p>뒤쪽이 이 테스트의 요점이다. 포워딩을 넓게 잡으면 API 오류가 200 짜리 HTML 로 보이고,
 * Swagger UI 가 조용히 사라진다. 그런 상태는 <b>화면이 잘 도는 것처럼 보이면서</b> 생기므로
 * 사람 눈에 늦게 띈다.
 *
 * <p>근거: {@code docs/spec/phase8-frontend.md} § 9.4
 */
@SpringBootTest
class SpaForwardingTest {

    @Autowired
    private WebApplicationContext context;

    @ParameterizedTest
    @ValueSource(strings = {"/plan", "/journal", "/backtest", "/projection"})
    void 화면_경로를_새로고침하면_화면이_돌아온다(String route) throws Exception {
        mockMvc().perform(get(route)).andExpect(forwardedUrl("/index.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api", "/api/trades/없는것", "/v3/api-docs", "/swagger-ui/index.html", "/assets/index-x.js"})
    void 프론트가_아닌_경로는_삼키지_않는다(String path) throws Exception {
        mockMvc().perform(get(path)).andExpect(forwardedUrl(null));
    }

    @Test
    void API_문서는_그대로_열린다() throws Exception {
        mockMvc().perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }
}
