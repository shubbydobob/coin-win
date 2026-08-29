package com.coinwin.readout.api;

import com.coinwin.readout.domain.FibonacciRetracement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 최근 스윙에 걸친 피보나치 되돌림.
 *
 * <p><b>레벨은 산술이고 그 이상은 아니다.</b> 0.618 은 스윙 폭의 61.8% 지점이라는 뜻이며, 그
 * 자리에서 가격이 되돌아온다는 것은 이 응답이 말하지 않는 별개의 주장이다. 흔히 인용되는
 * "61.8% 에서 70% 이상 반등한다" 는 근거 데이터가 붙은 출처를 찾지 못했고, 이 저장소는 같은
 * 종류의 전제를 7년 15,110봉에서 반증한 이력이 있다({@code docs/adr/021}).
 */
@Schema(description = "최근 스윙의 피보나치 되돌림 레벨")
public record FibonacciResponse(

        @Schema(description = "스윙 저점", example = "64000.00")
        BigDecimal low,

        @Schema(description = "스윙 고점", example = "79900.00")
        BigDecimal high,

        @Schema(description = """
                저점에서 고점으로 간 스윙인가. **되돌림을 어느 쪽에서 재는지가 이 값으로
                정해진다** — 오른 스윙은 고점에서 아래로, 내린 스윙은 저점에서 위로 잰다.""",
                example = "true")
        boolean upward,

        @Schema(description = """
                비율과 그 자리의 가격. 0.236 · 0.382 · 0.5 · 0.618 · 0.65 · 0.786 순이다.
                **0.618 과 0.65 가 골든 포켓의 두 끝**이고, 그 사이는 점이 아니라 띠다.""")
        List<FibonacciLevelResponse> levels,

        @Schema(description = """
                지금 가격이 골든 포켓(0.618~0.65) 안인가. **사실 하나이며 그 다음은 이 응답이
                말하지 않는다.**""",
                example = "false")
        boolean inGoldenPocket) {

    /** 받은 목록을 그대로 들지 않는다. {@code MacroQuoteListResponse} 와 같은 이유다. */
    public FibonacciResponse {
        levels = List.copyOf(levels);
    }

    /**
     * 레벨 한 줄. 맵으로 내면 생성된 타입에서 키가 사라져 화면이 순서를 잃는다.
     *
     * <p><b>이름이 {@code Response} 로 끝나야 한다.</b> 응답 필드를 required 로 만드는 규칙이
     * 이름의 접미사로 걸리기 때문이다 — 처음에 {@code FibonacciLevel} 로 두었더니 그 규칙을
     * 비켜가 생성된 타입에서 {@code ratio?: number} 가 됐고, 화면이 그 값을 optional 로
     * 다루게 됐다. Phase 8 이 "항상 있는 값과 진짜 null 인 값" 을 가르려고 세운 규칙이
     * 이름 하나로 무력해지는 자리다.
     */
    @Schema(description = "되돌림 레벨 하나")
    public record FibonacciLevelResponse(

            @Schema(description = "되돌림 비율", example = "0.618")
            BigDecimal ratio,

            @Schema(description = "그 비율의 가격", example = "70074.20")
            BigDecimal price) {
    }

    static FibonacciResponse from(FibonacciRetracement fib, BigDecimal close) {
        return new FibonacciResponse(
                fib.low().value(),
                fib.high().value(),
                fib.upward(),
                levelsOf(fib.levels()),
                fib.isInGoldenPocket(com.coinwin.common.domain.Price.of(close)));
    }

    private static List<FibonacciLevelResponse> levelsOf(Map<BigDecimal, com.coinwin.common.domain.Price> levels) {
        return levels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new FibonacciLevelResponse(entry.getKey(), entry.getValue().value()))
                .toList();
    }
}
