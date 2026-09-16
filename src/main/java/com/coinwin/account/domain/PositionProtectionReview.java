package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.journal.domain.OpenTrade;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 열려 있는 포지션 전부에 대해 "손절이 걸려 있나" 를 물어본 결과.
 *
 * <p><b>대조({@link PositionReconciliation}) 위에 얹는다.</b> 방향으로 짝을 짓는 일이 이미
 * 거기서 끝나 있고, 있어야 할 손절가는 그 짝의 기록 쪽에서만 나온다 — 따로 짝지으면 "방향마다
 * 최대 하나" 라는 규칙이 두 곳에 생기고 둘이 갈라질 수 있다.
 *
 * <p><b>기록에만 있는 거래는 여기 없다.</b> 거래소에 포지션이 없으면 걸 손절도 없다. 그쪽은
 * 대조가 {@link PositionMatch.RecordedOnly} 로 이미 말하고 있고, 두 화면이 같은 사실에 서로
 * 다른 경고를 내면 어느 쪽을 봐야 하는지 알 수 없어진다.
 *
 * @param protections 열려 있는 포지션마다 한 줄
 * @param observedAt 거래소 값을 읽은 시각
 */
public record PositionProtectionReview(List<PositionProtection> protections, Instant observedAt) {

    public PositionProtectionReview {
        DomainValues.required(protections, "보호 판정");
        DomainValues.required(observedAt, "관측 시각");
        protections = List.copyOf(protections);
    }

    /**
     * 대조 결과와 미체결 주문을 맞춰 본다.
     *
     * @param reconciliation 방향으로 이미 짝지어진 대조 결과
     * @param orders 거래소에 걸려 있는 포지션 종료 주문 전부
     */
    public static PositionProtectionReview of(
            PositionReconciliation reconciliation, List<ProtectiveOrder> orders) {
        DomainValues.required(reconciliation, "대조 결과");
        DomainValues.required(orders, "보호 주문");
        return new PositionProtectionReview(
                reconciliation.matches().stream()
                        .map(match -> protectionOf(match, orders))
                        .flatMap(Optional::stream)
                        .toList(),
                reconciliation.observedAt());
    }

    /** 사람이 지금 손을 대야 할 포지션. 전량이 덮인 것은 여기 없다. */
    public List<PositionProtection> unprotected() {
        return protections.stream()
                .filter(protection -> protection.coverage().needsAttention())
                .toList();
    }

    /**
     * 모든 포지션이 전량 덮여 있는가.
     *
     * <p><b>포지션이 하나도 없어도 참이다.</b> 걸 것이 없는 상태는 규칙을 어긴 상태가 아니다 —
     * 대조의 {@code isConsistent} 가 양쪽 모두 비었을 때 참인 것과 같다.
     */
    public boolean allProtected() {
        return unprotected().isEmpty();
    }

    private static Optional<PositionProtection> protectionOf(
            PositionMatch match, List<ProtectiveOrder> orders) {
        return actualOf(match).map(
                position -> PositionProtection.of(position, orders, plannedStopLossOf(match)));
    }

    /** 거래소에 실제로 열려 있는 쪽. 기록에만 있는 짝은 비어 있다. */
    private static Optional<ExchangePosition> actualOf(PositionMatch match) {
        return switch (match) {
            case PositionMatch.Agreed agreed -> Optional.of(agreed.actual());
            case PositionMatch.ExchangeOnly only -> Optional.of(only.actual());
            case PositionMatch.QuantityDiffers differs -> Optional.of(differs.actual());
            case PositionMatch.RecordedOnly ignored -> Optional.empty();
        };
    }

    /** 계획한 손절가. 앱 밖에서 연 포지션은 기록이 없으므로 비어 있다. */
    private static Optional<Price> plannedStopLossOf(PositionMatch match) {
        return recordedOf(match).map(trade -> trade.plan().stopLoss());
    }

    private static Optional<OpenTrade> recordedOf(PositionMatch match) {
        return switch (match) {
            case PositionMatch.Agreed agreed -> Optional.of(agreed.recorded());
            case PositionMatch.RecordedOnly only -> Optional.of(only.recorded());
            case PositionMatch.QuantityDiffers differs -> Optional.of(differs.recorded());
            case PositionMatch.ExchangeOnly ignored -> Optional.empty();
        };
    }
}
