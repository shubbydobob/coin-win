package com.coinwin.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * springdoc 이 실제로 문서를 만들어 내는지 확인하는 스모크 테스트.
 *
 * <p>Phase 0 에는 엔드포인트가 없다. 설정이 살아 있는지만 본다.
 * 실제 API 스펙 검증은 Phase 1 에서 엔드포인트가 생길 때 한다.
 */
@SpringBootTest
class OpenApiSmokeTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void api_문서가_생성되고_프로젝트_제목을_담는다() throws Exception {
        mockMvc().perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CoinWin API"));
    }
}
