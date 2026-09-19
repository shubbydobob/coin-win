package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import java.util.List;
import java.util.Optional;

/**
 * 한 사이클이 보는 세상의 전부 — 시장 · 판독 · 계좌 · 열린 포지션.
 *
 * <p><b>셋을 한 번에 읽는 것이 요점이다.</b> 따로 읽으면 같은 사이클 안에서 시장은 3초 전,
 * 계좌는 지금이 되고, 그 어긋남 위에서 내린 판단은 재현되지 않는다. 포트를 하나로 둔 것도
 * 같은 이유다 — 둘로 나누면 "함께 읽어야 한다" 가 규칙이 되고, 규칙은 잊힌다.
 *
 * <p>시각은 {@link MarketView} 가 한 번만 갖는다. 계좌 쪽에 또 두면 둘이 어긋날 수 있다.
 *
 * @param view 이 순간의 시장
 * @param account 한계를 판정하는 데 필요한 수 네 개
 * @param open 열려 있는 포지션. 없으면 비어 있다
 * @param reading 시장을 읽어낸 것 — 지표·대·매물대 · 호가 · 군중의 위치.
 *     <b>{@link MarketView} 와 나눠 둔 이유는 하나가 사실이고 하나가 해석이기 때문이다</b> —
 *     표시가와 캔들은 거래소가 준 것이고, 판독은 그 위에서 우리가 계산한 것이다.
 *     못 읽었을 수 있고 그때 비어 있다({@link MarketReading})
 * @param resting 걸려 있는 미체결 주문. <b>이것이 없으면 전략이 "손절이 이미 있는가" 를
 *     물을 수 없고, 사이클마다 같은 손절을 다시 건다</b>
 */
public record BotContext(
        MarketView view,
        MarketReading reading,
        AccountState account,
        Optional<BotPosition> open,
        List<PlacedOrder> resting) {

    public BotContext {
        DomainValues.required(view, "시장");
        DomainValues.required(reading, "판독");
        DomainValues.required(account, "계좌 상태");
        DomainValues.required(open, "열린 포지션");
        DomainValues.required(resting, "걸려 있는 주문");
        assertPositionCountAgrees(account, open);
        resting = List.copyOf(resting);
    }

    /** 이 포지션을 덮는 손절이 이미 걸려 있는가. */
    public boolean hasStopLoss() {
        return open.isPresent() && resting.stream()
                .anyMatch(order -> order.intent().kind() == OrderKind.STOP_LOSS
                        && order.intent().position() == open.orElseThrow().direction());
    }

    /**
     * 열린 포지션이 있다고 했으면 수도 0 이 아니어야 한다.
     *
     * <p>어긋나면 한계가 헛돈다 — 포지션이 있는데 수가 0 이면 동시 포지션 한계가 통과하고
     * <b>이미 열린 자리에 하나를 더 연다.</b> 두 값이 다른 곳에서 오므로 실제로 어긋날 수 있고,
     * 그때는 조용히 넘기는 것보다 터지는 것이 낫다.
     */
    private static void assertPositionCountAgrees(
            AccountState account, Optional<BotPosition> open) {
        if (open.isPresent() && account.openPositions() == 0) {
            throw new InvalidOrderException(
                    "열린 포지션이 있는데 계좌가 0 개라고 말한다 — 두 값이 어긋났다");
        }
    }
}
