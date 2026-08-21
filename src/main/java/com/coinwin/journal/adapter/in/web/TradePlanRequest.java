package com.coinwin.journal.adapter.in.web;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 진입 전에 남기는 계획.
 *
 * <p>{@code position} 모듈의 분석 요청과 필드가 겹치지만 같은 DTO 가 아니다. 그쪽은 계좌
 * 잔고와 리스크 비율을 함께 받아 <b>수량을 산출</b>하고, 이쪽은 계획을 <b>기록</b>할 뿐이다.
 * 공유하려 들면 기록 API 가 잔고를 요구하게 되고, 그러면 잔고가 바뀔 때마다 같은 계획이
 * 다르게 저장된다. 무엇보다 {@code api} 층은 서로를 참조할 수 없다(ArchUnit 규칙 2).
 */
@Schema(description = "진입 전 매매 계획", example = JournalApiExamples.PLAN_REQUEST)
public record TradePlanRequest(

        @Schema(description = "포지션 방향", example = "LONG")
        Direction direction,

        @Schema(description = "분할 진입 계획. 50% 분할이면 2건")
        List<PlannedEntryRequest> entries,

        @Schema(description = "손절가. 롱은 최저 진입가보다 낮아야 한다", example = "58000")
        BigDecimal stopLoss,

        @Schema(description = "익절가. 롱은 최고 진입가보다 높아야 한다", example = "64000")
        BigDecimal takeProfit,

        @Schema(description = "레버리지 배수", example = "10")
        Integer leverage) {

    public TradePlanRequest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    PositionPlan toPlan() {
        return new PositionPlan(direction, new EntryLadder(plannedEntries()),
                Price.of(stopLoss), Price.of(takeProfit), requiredLeverage());
    }

    private List<PlannedEntry> plannedEntries() {
        if (entries.isEmpty()) {
            throw new InvalidValueException("분할 진입 계획은(는) 최소 1건이어야 한다");
        }
        return entries.stream().map(PlannedEntryRequest::toEntry).toList();
    }

    private int requiredLeverage() {
        if (leverage == null) {
            throw new InvalidValueException("레버리지는(는) null 일 수 없다");
        }
        return leverage;
    }
}
