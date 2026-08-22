package com.coinwin.account.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.journal.domain.OpenTrade;
import com.coinwin.position.domain.Direction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 대조 한 번의 결과.
 *
 * <p><b>방향으로 짝을 짓는다.</b> 한 종목에 롱 포지션과 숏 포지션은 각각 최대 하나씩만 존재할
 * 수 있으므로 방향이면 충분하다. 심볼로 맞추려던 초안은 성립하지 않았다 — {@code OpenTrade}
 * 에 심볼이 없다. 이 프로젝트가 BTC 무기한 하나만 다루므로 기록이 종목을 적지 않는다.
 *
 * <p>그 제약이 더 나은 모양을 만들었다. <b>기록은 롱인데 거래소가 숏이면 짝이 지어지지 않고
 * {@link PositionMatch.RecordedOnly} 와 {@link PositionMatch.ExchangeOnly} 가 하나씩 나온다</b> —
 * 짝지어지지 않는 것 자체가 사실의 표현이다. 한 줄로 합쳤다면 "방향이 뒤집혔다" 는 가장 심각한
 * 사실이 수량 불일치처럼 보였을 것이다.
 *
 * <p><b>종목 거르기는 여기서 하지 않는다.</b> 이미 걸러진 목록을 받는다 — 거래소는 계좌의 모든
 * 심볼을 돌려주고, 그 필터는 "우리가 어느 종목을 보는가" 라는 설정에 속한다.
 *
 * @param matches 방향별 짝. 롱 · 숏 순서다
 * @param observedAt 거래소 값을 읽은 시각
 */
public record PositionReconciliation(List<PositionMatch> matches, Instant observedAt) {

    public PositionReconciliation {
        DomainValues.required(matches, "대조 결과");
        DomainValues.required(observedAt, "관측 시각");
        matches = List.copyOf(matches);
    }

    /**
     * 기록과 거래소를 방향으로 맞춰 본다.
     *
     * @param recorded 아직 닫히지 않은 기록. 방향마다 최대 하나여야 한다
     * @param actual 거래소 포지션. <b>이미 종목으로 걸러진</b> 목록이다
     */
    public static PositionReconciliation of(
            List<OpenTrade> recorded, List<ExchangePosition> actual, Instant observedAt) {
        DomainValues.required(recorded, "기록된 미청산 거래");
        DomainValues.required(actual, "거래소 포지션");

        List<PositionMatch> matches = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            matchFor(direction, recorded, actual).ifPresent(matches::add);
        }
        return new PositionReconciliation(matches, observedAt);
    }

    /** 전부 일치하는가. 짝이 하나도 없는 것(둘 다 비어 있음)도 일치다. */
    public boolean isConsistent() {
        return matches.stream().noneMatch(PositionMatch::isDiscrepancy);
    }

    /** 사람이 확인해야 할 것들. */
    public List<PositionMatch> discrepancies() {
        return matches.stream().filter(PositionMatch::isDiscrepancy).toList();
    }

    private static Optional<PositionMatch> matchFor(
            Direction direction, List<OpenTrade> recorded, List<ExchangePosition> actual) {
        Optional<OpenTrade> trade = only(recorded.stream()
                .filter(open -> open.plan().direction() == direction).toList(), "기록된 거래");
        Optional<ExchangePosition> position = only(actual.stream()
                .filter(open -> open.direction() == direction).toList(), "거래소 포지션");

        if (trade.isEmpty()) {
            return position.map(PositionMatch.ExchangeOnly::new);
        }
        if (position.isEmpty()) {
            return trade.map(PositionMatch.RecordedOnly::new);
        }
        return Optional.of(pair(trade.orElseThrow(), position.orElseThrow()));
    }

    private static PositionMatch pair(OpenTrade trade, ExchangePosition position) {
        return trade.quantity().equals(position.quantity())
                ? new PositionMatch.Agreed(trade, position)
                : new PositionMatch.QuantityDiffers(trade, position);
    }

    /**
     * 같은 방향에 둘 이상이면 던진다.
     *
     * <p>조용히 첫 번째를 고르면 나머지가 대조에서 사라진다 — 열려 있는 줄 모르는 포지션이
     * 생긴다는 뜻이고, 그것은 이 기능이 막으려는 상태 그 자체다.
     */
    private static <T> Optional<T> only(List<T> candidates, String label) {
        if (candidates.size() > 1) {
            throw new InvalidAccountDataException(
                    "같은 방향의 %s 가 둘 이상이다: %d 건".formatted(label, candidates.size()));
        }
        return candidates.stream().findFirst();
    }
}
