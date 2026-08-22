package com.coinwin.account.adapter.in.web;

import com.coinwin.account.domain.PositionMatch;
import com.coinwin.account.domain.PositionReconciliation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 기록과 거래소를 맞춰 본 결과.
 *
 * <p><b>일치 여부를 프론트가 판정하지 않는다.</b> 두 수를 화면에서 비교하면 그 비교 규칙이
 * 서버와 갈릴 수 있고, "스케일 8 까지 정확히 같아야 한다" 는 규칙이 두 곳에 생긴다.
 * 근거는 {@code docs/adr/020} — 프론트는 숫자를 만들지 않는다.
 */
@Schema(description = "기록의 미청산 거래와 거래소 포지션을 방향으로 맞춰 본 결과")
public record PositionReconciliationResponse(
        @Schema(description = "방향마다 한 줄. 짝이 지어지지 않은 쪽도 한 줄이다")
        List<PositionMatchResponse> matches,

        @Schema(description = "확인할 것이 하나도 없는가. 양쪽 모두 비어 있어도 참이다",
                example = "true")
        boolean consistent,

        @Schema(description = "거래소 값을 읽은 시각. 이 값은 다음 순간이면 달라진다",
                example = "2026-08-23T01:53:00Z")
        Instant observedAt) {

    /** 목록을 복사해 담는다. 부르는 쪽이 나중에 바꿔도 응답이 흔들리지 않는다. */
    public PositionReconciliationResponse {
        matches = List.copyOf(matches);
    }

    public static PositionReconciliationResponse from(PositionReconciliation reconciliation) {
        return new PositionReconciliationResponse(
                reconciliation.matches().stream().map(PositionMatchResponse::from).toList(),
                reconciliation.isConsistent(),
                reconciliation.observedAt());
    }

    /** 짝 하나. 어느 쪽이 비어 있는지가 곧 무슨 일이 있었는지다. */
    @Schema(description = "한 방향의 기록과 거래소를 맞춰 본 한 줄")
    public record PositionMatchResponse(
            @Schema(description = "이 줄이 어느 방향의 포지션에 대한 것인가",
                    example = "LONG")
            String direction,

            @Schema(description = """
                    맞춰 본 결과.
                    AGREED 는 방향과 수량이 같다.
                    RECORDED_ONLY 는 기록에는 열려 있는데 거래소에 없다 — 청산을 적지 않았을 수 있다.
                    EXCHANGE_ONLY 는 거래소에 있는데 기록에 없다 — 앱 밖에서 열었다.
                    QUANTITY_DIFFERS 는 둘 다 있는데 수량이 다르다 — 물타기나 부분 청산이 안 적혔다.""",
                    example = "AGREED")
            String outcome,

            @Schema(description = "사람이 확인할 것이 있는가", example = "false")
            boolean discrepancy,

            @Schema(description = "기록 쪽. 거래소에만 있는 포지션이면 null 이다",
                    nullable = true)
            RecordedSideResponse recorded,

            @Schema(description = "거래소 쪽. 기록에만 있는 거래면 null 이다", nullable = true)
            ExchangeSideResponse actual) {

        static PositionMatchResponse from(PositionMatch match) {
            return switch (match) {
                case PositionMatch.Agreed agreed -> new PositionMatchResponse(
                        agreed.direction().name(), "AGREED", false,
                        RecordedSideResponse.from(agreed.recorded()),
                        ExchangeSideResponse.from(agreed.actual()));
                case PositionMatch.RecordedOnly only -> new PositionMatchResponse(
                        only.direction().name(), "RECORDED_ONLY", true,
                        RecordedSideResponse.from(only.recorded()), null);
                case PositionMatch.ExchangeOnly only -> new PositionMatchResponse(
                        only.direction().name(), "EXCHANGE_ONLY", true,
                        null, ExchangeSideResponse.from(only.actual()));
                case PositionMatch.QuantityDiffers differs -> new PositionMatchResponse(
                        differs.direction().name(), "QUANTITY_DIFFERS", true,
                        RecordedSideResponse.from(differs.recorded()),
                        ExchangeSideResponse.from(differs.actual()));
            };
        }
    }
}
