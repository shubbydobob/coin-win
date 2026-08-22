package com.coinwin.backtest.api;

import com.coinwin.ai.application.port.in.SummarizeUseCase;
import com.coinwin.backtest.api.BacktestResultResponse.ComparisonResponse;
import com.coinwin.backtest.application.BacktestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지지·저항 반전 전략을 과거 캔들 위에서 돌린다.
 *
 * <p><b>저장된 캔들만 읽는다.</b> 거래소를 때리지 않으므로 같은 요청은 언제나 같은 결과를
 * 낸다. 캔들 수집은 {@code /api/market/candles/sync} 의 일이다.
 *
 * <p>주문을 내지 않는다. 미래를 예측하지도 않는다 — 과거에 이 규칙이 무엇을 했는지만 센다.
 */
@RestController
@RequestMapping("/api/backtests")
@Tag(name = "백테스트", description = "지지·저항 반전 전략을 과거 캔들 위에서 재현한다")
public class BacktestController {

    private final BacktestService backtestService;

    private final SummarizeUseCase summarize;

    public BacktestController(BacktestService backtestService, SummarizeUseCase summarize) {
        this.backtestService = backtestService;
        this.summarize = summarize;
    }

    @Operation(
            summary = "백테스트 실행",
            description = """
                    피벗 군집으로 만든 대의 근단에서 반전 방향으로 50% 분할 진입하고,
                    대 원단 너머에서 손절하며, 반대편 최근접 대에서 익절한다.
                    한 봉 안에서 손절과 익절이 모두 닿으면 손절로 처리한다 —
                    OHLC 로는 봉 내부 경로를 알 수 없으므로 보유자에게 불리한 쪽을 택한다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "요약 수치 · 거래 목록 · 자산 곡선"),
        @ApiResponse(responseCode = "400",
                description = "값 자체가 부적절하다. 알 수 없는 주기, 음수 자본, 뒤집힌 구간"),
        @ApiResponse(responseCode = "422",
                description = "값은 유효하나 백테스트로 성립하지 않는다. 워밍업(일목 77봉)을 "
                        + "채우지 못하는 구간, 터치 2회 미만의 대 설정")
    })
    @PostMapping
    public BacktestResultResponse run(@RequestBody RunBacktestRequest request) {
        return BacktestResultResponse.from(backtestService.run(request.toSpec()));
    }

    @Operation(
            summary = "지표 필터 온오프 비교",
            description = """
                    같은 캔들로 두 번 돌린다 — 일목·볼린저를 게이트로 쓴 실행과 쓰지 않은 실행.
                    필터가 실제로 값을 하는지는 비교로만 답할 수 있는 질문이다.
                    요청의 indicatorFilter 값은 무시된다.""")
    @PostMapping("/indicator-filter-comparison")
    public ComparisonResponse compareIndicatorFilter(@RequestBody RunBacktestRequest request) {
        return ComparisonResponse.from(
                backtestService.compareIndicatorFilter(request.toSpec()));
    }

    @Operation(
            summary = "백테스트 결과 요약 (AI)",
            description = """
                    같은 조건으로 백테스트를 돌리고 그 수치를 한국어 몇 문장으로 옮긴다.
                    요약에 나오는 수는 전부 응답의 facts 안에 있는 값이며,
                    없는 수가 들어간 요약은 만들어지지 않고 503 이 된다.

                    /api/ai 아래에 있지 않은 이유는 모듈 순환 때문이다 — ai 가 백테스트를
                    직접 돌리면 backtest 와 ai 가 서로를 참조하게 되고 ArchUnit 규칙 3 이
                    빌드를 세운다. 사실을 만들어 넘기는 쪽이 백테스트다.

                    앞으로 어떻게 하라는 말은 하지 않는다. 근거는 docs/adr/005.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "요약 문장과 그 문장이 쓴 수의 출처인 facts"),
        @ApiResponse(responseCode = "503",
                description = "AI 기능이 설정되지 않았거나(OPENAI_API_KEY), 모델이 원본에 없는 "
                        + "수를 썼다. 둘 다 사용자가 고칠 것이 아니라 다시 시도할 일이다")
    })
    @PostMapping("/narrative")
    public BacktestNarrativeResponse narrate(@RequestBody RunBacktestRequest request) {
        return BacktestNarrativeResponse.from(
                summarize.summarize(BacktestFacts.of(backtestService.run(request.toSpec()))));
    }

    @Operation(
            summary = "비용 유무 비교",
            description = """
                    같은 체결에 비용만 다르게 두 번 돌린다.
                    수수료가 엣지를 먹어 치우는지 — 전략이 이겼는데 계좌가 줄어드는지 — 를 낸다.""")
    @PostMapping("/cost-comparison")
    public ComparisonResponse compareCosts(@RequestBody RunBacktestRequest request) {
        return ComparisonResponse.from(backtestService.compareCosts(request.toSpec()));
    }
}
