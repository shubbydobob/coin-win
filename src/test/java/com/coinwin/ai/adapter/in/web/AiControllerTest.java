package com.coinwin.ai.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coinwin.ai.adapter.out.memory.InMemoryTradeIndexAdapter;
import com.coinwin.ai.application.port.out.AnswerQuestionPort;
import com.coinwin.ai.application.port.out.ExtractPlanPort;
import com.coinwin.ai.application.service.JournalQaService;
import com.coinwin.ai.application.service.PlanDraftService;
import com.coinwin.ai.application.service.TradeIndexingService;
import com.coinwin.ai.domain.DraftedEntry;
import com.coinwin.ai.domain.DraftedFields;
import com.coinwin.ai.domain.TradeDocument;
import com.coinwin.common.api.DomainExceptionHandler;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.JournalFixtures;
import com.coinwin.journal.adapter.out.memory.InMemoryTradeAdapter;
import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.application.service.TradeJournalService;
import com.coinwin.journal.domain.ClosedTrade;
import com.coinwin.position.domain.Direction;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP 경계 검증. 규칙은 도메인 테스트가 이미 증명했으므로 여기서는 <b>매핑과 상태 코드</b>만 본다.
 *
 * <p>모델은 스텁이다. 이 테스트가 답하는 질문은 "모델이 이렇게 답했을 때 사용자가 무엇을
 * 보는가" 이지 "모델이 잘 답하는가" 가 아니다.
 */
class AiControllerTest {

    private static final String DRAFT = "/api/ai/plan-draft";

    private static final String QUERY = "/api/ai/journal-query";

    private static final String REINDEX = "/api/ai/reindex";

    private static final String QUESTION = """
            {"question": "계획을 지킨 거래는 결과가 어땠나?"}""";

    /** 부르면 실패하는 스텁. 이 테스트가 모델을 부르지 않는다는 뜻이다. */
    private static final AnswerQuestionPort NOT_ASKED = (question, evidence) -> {
        throw new AssertionError("모델을 불렀다");
    };

    private static final String REQUEST = """
            {"text": "6만2천에 절반, 6만에 절반 롱. 손절 5만8천, 익절 6만8천, 10배"}""";

    private static MockMvc mockMvcFor(Optional<ExtractPlanPort> port) {
        return mockMvcFor(port, new InMemoryTradeIndexAdapter(), NOT_ASKED);
    }

    /** 계획 파싱·질의·색인 셋을 한 컨트롤러가 들고 있으므로 조립도 한 자리에서 한다. */
    private static MockMvc mockMvcFor(Optional<ExtractPlanPort> extractPlan,
            InMemoryTradeIndexAdapter index, AnswerQuestionPort answer) {
        AiController controller = new AiController(
                new PlanDraftService(extractPlan),
                new JournalQaService(Optional.of(index), Optional.of(answer)),
                new TradeIndexingService(journalWith(List.of()), Optional.of(index)));
        return MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new DomainExceptionHandler())
                .build();
    }

    private static MockMvc mockMvcReturning(DraftedFields fields) {
        return mockMvcFor(Optional.of(sentence -> fields));
    }

    @Test
    void 읽어낸_계획을_기존_요청과_같은_모양으로_돌려준다() throws Exception {
        DraftedFields fields = new DraftedFields(Direction.LONG,
                List.of(new DraftedEntry(Price.of("62000"), Percentage.of("50")),
                        new DraftedEntry(Price.of("60000"), Percentage.of("50"))),
                Price.of("58000"), Price.of("68000"), 10);

        mockMvcReturning(fields)
                .perform(post(DRAFT).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("LONG"))
                .andExpect(jsonPath("$.entries[0].price").value(62000))
                .andExpect(jsonPath("$.entries[0].allocation").value(50))
                .andExpect(jsonPath("$.entries[1].price").value(60000))
                .andExpect(jsonPath("$.stopLoss").value(58000))
                .andExpect(jsonPath("$.takeProfit").value(68000))
                .andExpect(jsonPath("$.leverage").value(10));
    }

    /** 수량은 손절가가 결정한다. 초안에 그 칸이 있으면 모델이 채운 숫자가 사이징을 건너뛴다. */
    @Test
    void 초안에는_총수량_칸이_없다() throws Exception {
        DraftedFields fields = new DraftedFields(Direction.LONG,
                List.of(new DraftedEntry(Price.of("62000"), Percentage.of("100"))),
                Price.of("58000"), Price.of("68000"), 10);

        mockMvcReturning(fields)
                .perform(post(DRAFT).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").doesNotExist())
                .andExpect(jsonPath("$.quantity").doesNotExist());
    }

    @Test
    void 읽어내지_못한_항목이_있으면_422로_무엇이_없는지_알려_준다() throws Exception {
        DraftedFields noStopNoLeverage = new DraftedFields(Direction.LONG,
                List.of(new DraftedEntry(Price.of("62000"), Percentage.of("100"))),
                null, Price.of("68000"), null);

        mockMvcReturning(noStopNoLeverage)
                .perform(post(DRAFT).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("손절가")))
                .andExpect(jsonPath("$.detail", containsString("레버리지")))
                .andExpect(jsonPath("$.detail", containsString("추측해서 채우지 않으므로")));
    }

    @Test
    void 빈_문장은_400이다() throws Exception {
        mockMvcReturning(null)
                .perform(post(DRAFT).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 키가_없으면_503이고_무엇이_필요한지_말해_준다() throws Exception {
        mockMvcFor(Optional.<ExtractPlanPort>empty())
                .perform(post(DRAFT).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("OPENAI_API_KEY")));
    }

    @Test
    void 답변은_근거_거래와_함께_내려온다() throws Exception {
        InMemoryTradeIndexAdapter index = indexOf(JournalFixtures.closedAtTarget());
        AnswerQuestionPort answer = (question, evidence) -> new AnswerQuestionPort.Answer(
                "계획을 지킨 거래 한 건이 있다.",
                List.of(evidence.getFirst().tradeId()));

        mockMvcFor(Optional.empty(), index, answer)
                .perform(post(QUERY).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("계획을 지킨 거래 한 건이 있다."))
                .andExpect(jsonPath("$.citedTradeIds").isNotEmpty())
                .andExpect(jsonPath("$.retrieved[0].tradeId").isNotEmpty())
                .andExpect(jsonPath("$.retrieved[0].summary").isNotEmpty());
    }

    /** 검색되지 않은 거래를 인용한 답은 나가지 못한다. 사용자가 고칠 것이 없으므로 503 이다. */
    @Test
    void 검색되지_않은_거래를_인용한_답은_503이다() throws Exception {
        InMemoryTradeIndexAdapter index = indexOf(JournalFixtures.closedAtTarget());
        AnswerQuestionPort answer = (question, evidence) ->
                new AnswerQuestionPort.Answer("없는 거래 이야기.", List.of("지어낸-식별자"));

        mockMvcFor(Optional.empty(), index, answer)
                .perform(post(QUERY).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("지어낸-식별자")));
    }

    /** 찾은 것이 없으면 모델을 부르지 않는다. 스텁이 불리면 그 자리에서 실패한다. */
    @Test
    void 검색_결과가_없으면_모델을_부르지_않는다() throws Exception {
        AnswerQuestionPort neverCalled = (question, evidence) -> {
            throw new AssertionError("모델을 불렀다");
        };

        mockMvcFor(Optional.empty(), new InMemoryTradeIndexAdapter(), neverCalled)
                .perform(post(QUERY).contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("기록이 없다")))
                .andExpect(jsonPath("$.citedTradeIds").isEmpty());
    }

    @Test
    void 빈_질문은_400이다() throws Exception {
        mockMvcFor(Optional.empty())
                .perform(post(QUERY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 근거_거래_수가_범위_밖이면_400이다() throws Exception {
        mockMvcFor(Optional.empty())
                .perform(post(QUERY).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "지난 거래는?", "topK": 500}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 재색인은_만들어진_문서_수를_돌려준다() throws Exception {
        mockMvcFor(Optional.empty())
                .perform(post(REINDEX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(0));
    }

    private static InMemoryTradeIndexAdapter indexOf(ClosedTrade... trades) {
        InMemoryTradeIndexAdapter index = new InMemoryTradeIndexAdapter();
        index.save(TradeDocument.over(List.of(trades)));
        return index;
    }

    private static QueryJournalUseCase journalWith(List<ClosedTrade> trades) {
        InMemoryTradeAdapter store = new InMemoryTradeAdapter();
        trades.forEach(store::save);
        return new TradeJournalService(store, store, Clock.systemUTC(), event -> { });
    }
}
