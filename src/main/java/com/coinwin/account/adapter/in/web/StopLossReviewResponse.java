package com.coinwin.account.adapter.in.web;

import com.coinwin.account.domain.PositionProtection;
import com.coinwin.account.domain.PositionProtectionReview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 열려 있는 포지션마다 손절이 걸려 있는가.
 *
 * <p><b>판정을 프론트가 하지 않는다.</b> "주문 목록에 STOP 이 있나" 를 화면에서 세면 추격
 * 손절을 손절로 칠지, 수량이 모자란 것을 어떻게 볼지가 두 곳에 생긴다 —
 * {@code docs/adr/020} 이 금지한 자리다.
 */
@Schema(description = "열려 있는 포지션이 손절로 덮여 있는가")
public record StopLossReviewResponse(
        @Schema(description = "거래소에 열려 있는 포지션마다 한 줄. 기록에만 있는 거래는 없다")
        List<PositionProtectionResponse> protections,

        @Schema(description = "전부 전량 덮여 있는가. 포지션이 하나도 없어도 true 다",
                example = "false")
        boolean allProtected,

        @Schema(description = "거래소 값을 읽은 시각", example = "2026-09-16T04:10:00Z")
        Instant observedAt) {

    public StopLossReviewResponse {
        protections = List.copyOf(protections);
    }

    public static StopLossReviewResponse from(PositionProtectionReview review) {
        return new StopLossReviewResponse(
                review.protections().stream().map(PositionProtectionResponse::from).toList(),
                review.allProtected(),
                review.observedAt());
    }

    /** 포지션 하나의 보호 상태. */
    @Schema(description = "포지션 하나가 손절로 얼마나 덮여 있는가")
    public record PositionProtectionResponse(
            @Schema(description = "이 줄이 어느 방향의 포지션인가", example = "SHORT",
                    allowableValues = {"LONG", "SHORT"})
            String direction,

            @Schema(description = """
                    NONE 은 손절이 하나도 없다 — 규칙상 존재할 수 없는 상태다.
                    PARTIAL 은 손절이 있는데 전량을 덮지 못한다(물타기 뒤에 흔하다).
                    FULL 은 전량이 덮여 있다.""",
                    example = "NONE", allowableValues = {"NONE", "PARTIAL", "FULL"})
            String coverage,

            @Schema(description = "보유 수량", example = "0.11400000")
            BigDecimal quantity,

            @Schema(description = "그중 손절로 덮인 수량", example = "0.00000000")
            BigDecimal stoppedQuantity,

            @Schema(description = """
                    기록된 계획의 손절가. **앱 밖에서 연 포지션이면 null 이다** —
                    있어야 할 손절가를 지어내지 않는다. 손절 거리의 기본값은 아직 재지 않았고,
                    재지 않은 수를 놓으면 사람이 그것을 기준으로 읽는다.""",
                    nullable = true, example = "65280.00")
            BigDecimal plannedStopLoss,

            @Schema(description = """
                    익절은 걸어 두었는데 손절이 없다. 아무것도 안 건 것과 다른 사실이다 —
                    버는 쪽만 준비하고 잃는 쪽을 비워 둔 것이다.""",
                    example = "true")
            boolean takeProfitWithoutStopLoss,

            @Schema(description = """
                    덮이지 않은 수량이 계획된 손절가까지 갔을 때 잃는 돈(USDT).
                    계획이 없거나 전량이 덮여 있으면 null 이다 — 0 은 '위험 없음'으로 읽힌다.""",
                    nullable = true, example = "183.24")
            BigDecimal exposureWithoutStop,

            @Schema(description = "이 포지션에 걸려 있는 종료 주문 전부")
            List<ProtectiveOrderResponse> orders) {

        public PositionProtectionResponse {
            orders = List.copyOf(orders);
        }

        static PositionProtectionResponse from(PositionProtection protection) {
            return new PositionProtectionResponse(
                    protection.position().direction().name(),
                    protection.coverage().name(),
                    protection.position().quantity().value(),
                    protection.stoppedQuantity().value(),
                    protection.plannedStopLoss().map(price -> price.value()).orElse(null),
                    protection.takeProfitWithoutStopLoss(),
                    protection.exposureWithoutStop().map(money -> money.value()).orElse(null),
                    protection.orders().stream().map(ProtectiveOrderResponse::from).toList());
        }
    }
}
