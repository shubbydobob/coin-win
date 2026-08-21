package com.coinwin.journal.adapter.in.web;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.domain.Exit;
import com.coinwin.journal.domain.ExitReason;
import com.coinwin.journal.domain.TradeClosure;
import com.coinwin.journal.domain.TradeCosts;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 청산 기록.
 *
 * <p><b>손익을 받지 않는다.</b> 청산가와 체결 내역이 있으면 도메인이 계산할 수 있고, 계산할 수
 * 있는 것을 입력받으면 둘이 어긋나 있어도 알 수 없다. 받는 것은 재현할 수 없는 것 — 거래소가
 * 정하는 수수료와 펀딩비 — 뿐이다.
 */
@Schema(description = "포지션 청산 기록", example = JournalApiExamples.CLOSE_REQUEST)
public record CloseTradeRequest(

        @Schema(description = "청산 체결가", example = "64000")
        BigDecimal exitPrice,

        @Schema(description = "청산 시각 (UTC). 마지막 진입 체결보다 앞설 수 없다",
                example = "2026-08-01T09:00:00Z")
        Instant exitAt,

        @Schema(description = "왜 닫았는가. 계획 준수 여부가 이 값으로 갈린다",
                example = "PLANNED_TARGET")
        ExitReason exitReason,

        @Schema(description = "이 거래에 나간 수수료 (USDT). 음수일 수 없다", example = "5.00")
        BigDecimal fees,

        @Schema(description = "낸 펀딩비 (USDT). 받았으면 음수", example = "1.20")
        BigDecimal funding) {

    TradeClosure toClosure() {
        return new TradeClosure(
                new Exit(Price.of(exitPrice), exitAt),
                exitReason,
                new TradeCosts(Money.of(fees), Money.of(funding)));
    }
}
