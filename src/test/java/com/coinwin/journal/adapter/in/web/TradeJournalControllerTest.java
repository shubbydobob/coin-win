package com.coinwin.journal.adapter.in.web;

import static com.coinwin.journal.JournalFixtures.PLANNED_AT;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.adapter.out.memory.InMemoryTradeAdapter;
import com.coinwin.journal.application.service.TradeJournalService;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.PlannedTrade;
import com.coinwin.journal.domain.TradeId;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP 경계 검증. 계산은 도메인·서비스 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태
 * 코드</b>만 본다.
 *
 * <p>{@code @SpringBootTest} 를 쓰지 않는 이유는 {@code MarketControllerTest} 와 같다 — 전체
 * 컨텍스트를 띄우면 진짜 JPA 어댑터가 주입되어 HTTP 매핑을 보려는 테스트가 DB 에 매달린다.
 */
class TradeJournalControllerTest {

    private static final String TRADES = "/api/trades";

    private InMemoryTradeAdapter store;
    private MockMvc mockMvc;

    @BeforeEach
    void 컨트롤러를_조립한다() {
        store = new InMemoryTradeAdapter();
        TradeJournalService service = new TradeJournalService(
                store, store, Clock.fixed(PLANNED_AT, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TradeJournalController(service, service))
                .setControllerAdvice(new DomainExceptionHandler())
                .build();
    }

    /** 계획 → 체결 → 청산이 세 번의 POST 로 이어지고 마지막 응답에 손익이 실린다. */
    @Test
    void 세_번의_요청으로_한_거래가_기록된다() throws Exception {
        mockMvc.perform(json(post(TRADES), JournalApiExamples.PLAN_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PLANNED"))
                .andExpect(jsonPath("$.entry").doesNotExist())
                .andExpect(jsonPath("$.plan.riskRewardRatio").value(3.00));
        String id = onlyActiveTradeId();

        mockMvc.perform(json(post(TRADES + "/" + id + "/fills"), JournalApiExamples.FILLS_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.entry.averageEntryPrice").value(59500.00))
                .andExpect(jsonPath("$.entry.rationale").value("4h 59,000 지지 3회 확인"))
                .andExpect(jsonPath("$.outcome").doesNotExist());

        mockMvc.perform(json(post(TRADES + "/" + id + "/closure"),
                        JournalApiExamples.CLOSE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"))
                .andExpect(jsonPath("$.outcome.grossPnl").value(450.00))
                .andExpect(jsonPath("$.outcome.realizedPnl").value(443.80))
                .andExpect(jsonPath("$.outcome.lossIfStopHonored").value(-155.00))
                .andExpect(jsonPath("$.outcome.followedPlan").value(true));
    }

    @Test
    void 집계는_계획_준수_쪽과_위반_쪽을_갈라서_낸다() throws Exception {
        givenTwoClosedTrades();

        mockMvc.perform(get(TRADES + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(2))
                .andExpect(jsonPath("$.followed.trades").value(1))
                .andExpect(jsonPath("$.broken.trades").value(1))
                .andExpect(jsonPath("$.planAdherence").value(50.0000))
                .andExpect(jsonPath("$.intervals.gaps").value(1))
                .andExpect(jsonPath("$.intervals.overlaps").value(0));
    }

    @Test
    void 조회_조건은_목록과_집계에_똑같이_걸린다() throws Exception {
        givenTwoClosedTrades();

        mockMvc.perform(get(TRADES).param("followedPlan", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].outcome.exitReason").value("HELD_PAST_STOP"));
        mockMvc.perform(get(TRADES + "/summary").param("followedPlan", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTrades").value(1));
    }

    @Test
    void 진행_중인_거래만_따로_낸다() throws Exception {
        store.save(JournalFixtures.plannedAt(PLANNED_AT));
        store.save(JournalFixtures.closedEndingAt(
                PLANNED_AT.plusSeconds(86_400), ExitReason.PLANNED_TARGET));

        mockMvc.perform(get(TRADES + "/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].state").value("PLANNED"));
    }

    @Test
    void 없는_거래를_조회하면_404다() throws Exception {
        mockMvc.perform(get(TRADES + "/" + TradeId.random()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("대상을 찾을 수 없다"));
    }

    /** 식별자 형식 오류는 값의 문제라 400 이다. 없는 거래(404)와 구분된다. */
    @Test
    void 식별자가_UUID가_아니면_400이다() throws Exception {
        mockMvc.perform(get(TRADES + "/삼번거래"))
                .andExpect(status().isBadRequest());
    }

    /** 진입 근거를 비우면 거부한다. 근거 없는 기록은 사후 분석에 쓸 수 없다. */
    @Test
    void 진입_근거가_비면_422다() throws Exception {
        String id = givenPlannedTrade();

        mockMvc.perform(json(post(TRADES + "/" + id + "/fills"),
                        JournalApiExamples.FILLS_REQUEST.replace("4h 59,000 지지 3회 확인", "  ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("진입 근거는 비워 둘 수 없다")));
    }

    @Test
    void 체결_없이_청산하면_422다() throws Exception {
        String id = givenPlannedTrade();

        mockMvc.perform(json(post(TRADES + "/" + id + "/closure"),
                        JournalApiExamples.CLOSE_REQUEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("체결됨 상태의 거래에만")));
    }

    private void givenTwoClosedTrades() {
        store.save(JournalFixtures.closedEndingAt(
                PLANNED_AT.plusSeconds(86_400), ExitReason.PLANNED_TARGET));
        store.save(JournalFixtures.closedEndingAt(
                PLANNED_AT.plusSeconds(259_200), ExitReason.HELD_PAST_STOP));
    }

    private String givenPlannedTrade() {
        PlannedTrade planned = JournalFixtures.plannedAt(PLANNED_AT);
        store.save(planned);
        return planned.id().toString();
    }

    /** 응답 본문에서 식별자를 파싱하지 않고 저장소에서 읽는다. 문자열 파싱은 부서지기 쉽다. */
    private String onlyActiveTradeId() {
        return store.findActive().getFirst().id().toString();
    }

    private static MockHttpServletRequestBuilder json(
            MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
