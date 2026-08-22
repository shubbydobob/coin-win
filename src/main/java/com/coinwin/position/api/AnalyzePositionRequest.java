package com.coinwin.position.api;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import com.coinwin.position.domain.RiskBudget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 진입 전 계획과 계좌 상태.
 *
 * <p>수량은 받지 않는다. 손절가와 리스크 비율이 수량을 결정하기 때문이다.
 */
@Schema(description = "분할 진입 계획과 계좌 상태", example = PositionApiExamples.REQUEST)
public record AnalyzePositionRequest(

        @Schema(description = "포지션 방향", example = "LONG")
        Direction direction,

        @Schema(description = "분할 진입 계획. 50% 분할이면 2건")
        List<PositionEntryRequest> entries,

        @Schema(description = "손절가. 롱은 최저 진입가보다 낮아야 하고, 숏은 최고 진입가보다 높아야 한다",
                example = "56000")
        BigDecimal stopLoss,

        @Schema(description = "익절가. 롱은 최고 진입가보다 높아야 하고, 숏은 최저 진입가보다 낮아야 한다",
                example = "66000")
        BigDecimal takeProfit,

        @Schema(description = "레버리지 배수. 청산가와 필요 증거금이 이 값에 따라 달라진다", example = "10")
        Integer leverage,

        @Schema(description = "계좌 잔고 (USDT)", example = "800")
        BigDecimal accountBalance,

        @Schema(description = "이 거래 한 건에 걸 잔고의 비율. 2 는 2% 를 뜻한다", example = "2")
        BigDecimal riskPercent) {

    /**
     * 넘어온 리스트를 그대로 들고 있지 않는다. 요청 객체는 도메인 변환 전까지 살아 있고,
     * 그 사이에 원본이 바뀌면 검증한 값과 변환한 값이 달라진다.
     */
    public AnalyzePositionRequest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    PositionPlan toPlan() {
        return new PositionPlan(
                direction,
                new EntryLadder(plannedEntries()),
                Price.of(stopLoss),
                Price.of(takeProfit),
                requiredLeverage());
    }

    RiskBudget toBudget() {
        return new RiskBudget(Money.of(accountBalance), Percentage.of(riskPercent));
    }

    private List<PlannedEntry> plannedEntries() {
        if (entries.isEmpty()) {
            throw new InvalidValueException("분할 진입 계획은(는) 최소 1건이어야 한다");
        }
        return entries.stream().map(PositionEntryRequest::toEntry).toList();
    }

    private int requiredLeverage() {
        if (leverage == null) {
            throw new InvalidValueException("레버리지는(는) null 일 수 없다");
        }
        return leverage;
    }
}
