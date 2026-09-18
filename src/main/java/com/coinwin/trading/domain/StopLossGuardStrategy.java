package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.util.List;

/**
 * <b>진입은 하지 않는다. 손절 없는 포지션에 손절을 건다.</b>
 *
 * <p>{@code docs/spec/exit-automation.md} 의 규칙 R1·R2 를 그대로 옮긴 것이고, 이 저장소에서
 * <b>엣지를 요구하지 않는 유일한 규칙</b>이다 — 새 수익을 만드는 것이 아니라 왼쪽 꼬리를
 * 자를 뿐이라 "누가 나에게 이 돈을 주는가" 를 통과할 필요가 없다({@code how-money-is-made.md}).
 * 그래서 진입 규칙이 없는 지금도 꽂을 수 있다.
 *
 * <p><b>R2 가 공짜로 따라온다.</b> 사이클마다 "손절이 있는가" 를 다시 물으므로, 사람이
 * 거래소 화면에서 손절을 지우면 다음 사이클에 다시 걸린다. 지우는 것이 능동적 행동이 되고
 * 그 행동은 몇 초 뒤 되돌려진다 — <b>마찰의 방향이 뒤집힌다.</b>
 *
 * <p>막는 것이 무엇인지는 수로 적혀 있다. 64,000 숏을 78,000까지 들고 가 −2,000 을 낸 그
 * 거래에 2% 손절 하나면 −183 이다({@code exit-automation.md} § 3).
 *
 * <p><b>거리는 근거 있는 수가 아니다.</b> 얼마나 멀어야 무효화만 잡고 잡음에 안 걸리는지는
 * 이 저장소가 잰 적이 없다({@code exit-automation.md} § 4 — 기준이 손익비가 아니라 MAE
 * 분포다). 자리표시자라는 것이 화면과 설정 양쪽에 적혀 있다.
 *
 * @param distance 표시가에서 손절까지의 거리(%)
 */
public record StopLossGuardStrategy(Percentage distance) implements TradingStrategy {

    public StopLossGuardStrategy {
        DomainValues.required(distance, "손절 거리");
        if (distance.value().signum() <= 0) {
            throw new InvalidOrderException("손절 거리는 0 보다 커야 한다");
        }
    }

    @Override
    public String name() {
        return "손절 지킴이 (진입 안 함, 거리 %s%%)".formatted(distance.value().toPlainString());
    }

    /**
     * 열려 있는데 손절이 없으면 건다. 그 밖에는 아무것도 하지 않는다.
     *
     * <p><b>평단이 아니라 표시가에서 잰다.</b> 이미 크게 밀린 포지션에 평단 기준으로 걸면
     * 손절이 지금 가격 반대편에 놓여 <b>즉시 체결된다</b> — 그것은 손절이 아니라 시장가 청산이고,
     * 사람이 원한 적 없는 매매다. 표시가 기준이면 어느 상태에서 걸든 "여기서 이만큼 더 가면
     * 끊는다" 라는 같은 뜻이 된다.
     */
    @Override
    public List<OrderIntent> decide(BotContext now) {
        DomainValues.required(now, "사이클 컨텍스트");
        if (now.open().isEmpty() || now.hasStopLoss()) {
            return List.of();
        }
        Direction direction = now.open().orElseThrow().direction();
        Price mark = now.view().mark();
        return List.of(OrderIntent.protectAll(now.view().symbol(), direction,
                new OrderIntent.Protection(OrderKind.STOP_LOSS, triggerFor(direction, mark), mark)));
    }

    /** 손절은 불리한 쪽에 놓인다 — 롱이면 아래, 숏이면 위. */
    private Price triggerFor(Direction direction, Price mark) {
        Money offset = distance.applyTo(mark.asAmount());
        return direction == Direction.LONG ? mark.minus(offset) : mark.plus(offset);
    }
}
