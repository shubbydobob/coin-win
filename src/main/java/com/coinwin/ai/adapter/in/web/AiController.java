package com.coinwin.ai.adapter.in.web;

import com.coinwin.ai.application.port.in.AskJournalUseCase;
import com.coinwin.ai.application.port.in.DraftPlanUseCase;
import com.coinwin.ai.application.port.in.IndexTradesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자연어를 구조로 바꾸는 엔드포인트.
 *
 * <p><b>여기서 나온 것은 무엇도 자동으로 실행되지 않는다.</b> 저장도 주문도 계산도 하지 않고
 * 초안만 돌려준다. 매수·매도 시점 추천과 가격 예측은 요청받아도 하지 않는다 —
 * 근거는 {@code docs/adr/005}.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 보조", description = "자연어를 구조로 바꾸고 수치를 문장으로 바꾼다. 판단하지 않는다")
public class AiController {

    private final DraftPlanUseCase draftPlan;

    private final AskJournalUseCase askJournal;

    private final IndexTradesUseCase indexTrades;

    public AiController(DraftPlanUseCase draftPlan, AskJournalUseCase askJournal,
            IndexTradesUseCase indexTrades) {
        this.draftPlan = draftPlan;
        this.askJournal = askJournal;
        this.indexTrades = indexTrades;
    }

    @Operation(
            summary = "자연어 → 매매 계획 초안",
            description = """
                    문장에 있는 값만 옮겨 적는다. 없는 값은 추측해서 채우지 않고
                    무엇이 없는지 되물어 온다 — 잘못 채운 진입가는 잘못된 포지션 사이즈로
                    이어지고 그 오류는 계산 계층 전체를 조용히 오염시킨다.

                    총수량은 초안에 없다. 수량은 손절가와 리스크 예산이 결정하는 값이므로
                    /api/position-plans/analysis 가 계산한다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "문장에서 읽어낸 계획 초안. 폼을 채울 뿐 제출하지 않는다"),
        @ApiResponse(responseCode = "400", description = "문장이 비어 있다"),
        @ApiResponse(responseCode = "422",
                description = "문장에서 읽어내지 못한 항목이 있거나, 읽어낸 값이 계획으로 "
                        + "성립하지 않는다. 응답 본문이 무엇이 빠졌는지 알려 준다"),
        @ApiResponse(responseCode = "503",
                description = "AI 기능이 설정되지 않았다. OPENAI_API_KEY 가 필요하다")
    })
    @PostMapping("/plan-draft")
    public PlanDraftResponse draftPlan(@RequestBody PlanDraftRequest request) {
        return PlanDraftResponse.from(draftPlan.draftFrom(request.text()));
    }

    @Operation(
            summary = "매매 기록 질의",
            description = """
                    지난 매매 기록에서 질문에 가까운 거래를 찾아 그것만 근거로 답한다.
                    답변과 함께 근거 거래 식별자가 내려오므로 원본 기록과 대조할 수 있다.
                    검색 결과가 없으면 모델을 부르지 않고 "해당하는 기록이 없다" 를 돌려준다 —
                    없는 것에 대해 문장을 만들 기회 자체를 주지 않는다.

                    "손실 직후에 들어간 거래" 처럼 순서를 묻는 질문에 답할 수 있는 이유는
                    색인 시점에 그 사실을 미리 계산해 두기 때문이다. 유사도만으로는
                    "직후" 를 찾을 수 없다.

                    앞으로의 매매는 묻지 않는다. 이미 일어난 일에 대해서만 답한다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "답변과 근거 거래 식별자. 원본 기록과 대조할 수 있다"),
        @ApiResponse(responseCode = "400", description = "질문이 비었거나 근거 거래 수가 범위 밖이다"),
        @ApiResponse(responseCode = "503",
                description = "AI 기능이 설정되지 않았거나, 답변이 검색되지 않은 거래를 인용했다")
    })
    @PostMapping("/journal-query")
    public JournalAnswerResponse askJournal(@RequestBody JournalQueryRequest request) {
        return JournalAnswerResponse.from(
                askJournal.ask(request.question(), request.effectiveTopK()));
    }

    @Operation(
            summary = "매매 기록 재색인",
            description = """
                    청산된 거래 전부로 색인을 다시 만든다. 문서 형식이나 임베딩 모델이 바뀌면
                    이것 없이는 과거 기록이 구버전으로 남는다.

                    청산 시에는 자동으로 색인되므로 평소에는 부를 일이 없다.
                    색인은 파생 데이터이고 진실의 원천은 매매 기록이므로,
                    언제 몇 번을 불러도 기록이 달라지지 않는다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "다시 색인한 거래 건수"),
        @ApiResponse(responseCode = "503", description = "AI 기능이 설정되지 않았다")
    })
    @PostMapping("/reindex")
    public ReindexResponse reindex() {
        return new ReindexResponse(indexTrades.reindexAll());
    }
}
