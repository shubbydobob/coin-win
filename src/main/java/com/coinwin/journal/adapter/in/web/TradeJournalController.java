package com.coinwin.journal.adapter.in.web;

import com.coinwin.journal.application.port.in.QueryJournalUseCase;
import com.coinwin.journal.application.port.in.RecordTradeUseCase;
import com.coinwin.journal.domain.TradeId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매매 기록. 계획 → 체결 → 청산이 세 번의 POST 이고, 그 순서로만 진행된다.
 *
 * <p>주문을 내지 않는다. 여기에 오는 것은 <b>이미 일어난 일</b>이고, 이 모듈은 그것을 나중에
 * 되물을 수 있는 형태로 남길 뿐이다. 주문 실행은 {@code scope.md} 가 제외한 범위다.
 */
@RestController
@RequestMapping("/api/trades")
@Tag(name = "매매 기록", description = "계획을 남기고 체결·청산을 기록해 계획 준수 여부별로 집계한다")
public class TradeJournalController {

    private final RecordTradeUseCase recordTrade;
    private final QueryJournalUseCase queryJournal;

    public TradeJournalController(
            RecordTradeUseCase recordTrade, QueryJournalUseCase queryJournal) {
        this.recordTrade = recordTrade;
        this.queryJournal = queryJournal;
    }

    @Operation(summary = "진입 전 계획 저장",
            description = """
                    진입하기 전에 남긴다. 진입 후에 적으면 결과를 아는 채로 쓰게 되고,
                    그런 기록으로는 계획 준수 여부를 판정할 수 없다.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "저장됨. 이후 요청에 쓸 식별자를 돌려준다"),
        @ApiResponse(responseCode = "400", description = "값 자체가 부적절하다"),
        @ApiResponse(responseCode = "422", description = "계획으로 성립하지 않는다")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradeResponse plan(@RequestBody TradePlanRequest request) {
        return TradeResponse.from(recordTrade.planTrade(request.toPlan()));
    }

    @Operation(summary = "진입 체결 기록",
            description = "체결 내역과 진입 시점의 시장 상태를 함께 받는다. 맥락은 이 순간에만 존재한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "404", description = "그런 거래가 없다"),
        @ApiResponse(responseCode = "422", description = "계획 상태가 아니거나 체결이 계획보다 앞선다")
    })
    @PostMapping("/{id}/fills")
    public TradeResponse recordFills(
            @PathVariable String id, @RequestBody RecordFillsRequest request) {
        return TradeResponse.from(recordTrade.recordFills(
                TradeId.of(id), request.toEntries(), request.toContext()));
    }

    @Operation(summary = "청산 기록",
            description = "손익은 받지 않는다. 청산가와 체결 내역으로 도메인이 계산한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "404", description = "그런 거래가 없다"),
        @ApiResponse(responseCode = "422", description = "열려 있지 않거나 청산이 진입보다 앞선다")
    })
    @PostMapping("/{id}/closure")
    public TradeResponse close(
            @PathVariable String id, @RequestBody CloseTradeRequest request) {
        return TradeResponse.from(recordTrade.closeTrade(TradeId.of(id), request.toClosure()));
    }

    @Operation(summary = "끝난 거래 목록", description = "조건에 드는 거래를 진입 시각 오름차순으로 낸다")
    @GetMapping
    public List<TradeResponse> closedTrades(TradeQueryParams params) {
        return queryJournal.closedTrades(params.toQuery()).stream()
                .map(TradeResponse::from).toList();
    }

    @Operation(summary = "집계",
            description = "목록과 같은 조건이 걸린다. 계획 준수 쪽과 위반 쪽을 갈라서 낸다.")
    @GetMapping("/summary")
    public JournalSummaryResponse summary(TradeQueryParams params) {
        return JournalSummaryResponse.from(queryJournal.summarize(params.toQuery()));
    }

    @Operation(summary = "진행 중인 거래", description = "세워 둔 계획과 아직 닫히지 않은 포지션")
    @GetMapping("/active")
    public List<TradeResponse> activeTrades() {
        return queryJournal.activeTrades().stream().map(TradeResponse::from).toList();
    }

    @Operation(summary = "거래 한 건 조회")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "그런 거래가 없다"))
    @GetMapping("/{id}")
    public TradeResponse trade(@PathVariable String id) {
        return TradeResponse.from(queryJournal.trade(TradeId.of(id)));
    }
}
