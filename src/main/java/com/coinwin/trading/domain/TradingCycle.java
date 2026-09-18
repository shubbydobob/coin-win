package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 봇이 한 번 깨어나서 한 일의 전부. <b>이 기록이 봇의 산출물이다.</b>
 *
 * <p>무엇을 보고 무엇을 판단해 무엇을 냈는지가 사이클마다 남지 않으면, 나중에 "왜 그때
 * 들어갔나" 에 답할 수 없다. {@code journal} 이 사람의 매매에 대해 갖는 것을 봇에 대해서도
 * 갖는다 — 다른 점은 이쪽은 <b>아무것도 안 한 사이클도 기록한다</b>는 것이다. 대부분의
 * 사이클이 그렇고, 그 침묵이 정상이라는 것이 기록에 남아야 한다.
 *
 * <p><b>거부를 낸 주문과 같은 자리에 담지 않는다.</b> 한 목록에 섞으면 막힌 것과 나간 것이
 * 구별되지 않고, 그러면 "봇이 왜 안 들어갔나" 가 답할 수 없는 질문이 된다.
 *
 * @param at 이 사이클이 본 시각. {@link MarketView} 가 말한 것을 그대로 쓴다
 * @param mode 어느 모드로 돌았나. <b>장부 기록과 실계좌 기록이 한 표에 섞이면 안 된다</b>
 * @param strategy 판단한 전략의 이름
 * @param judged 전략이 낸 주문 전부와 그 판정. 비어 있는 것이 정상이다
 * @param placed 실제로 브로커에 닿은 것
 * @param cancelled 지운 주문. <b>손절이 사라진 것도 사건이다</b> — 안 적으면 R4 가 손절을
 *     옮긴 것과 누가 지운 것이 기록에서 구별되지 않는다
 * @param halted 봇이 멈춘 이유. 있으면 전략에게 묻지도 않았다는 뜻이다
 */
public record TradingCycle(
        Instant at,
        TradingMode mode,
        String strategy,
        List<RiskVerdict> judged,
        List<PlacedOrder> placed,
        List<OrderId> cancelled,
        Optional<String> halted) {

    public TradingCycle {
        DomainValues.required(at, "관측 시각");
        DomainValues.required(mode, "모드");
        DomainValues.required(judged, "판정");
        DomainValues.required(placed, "낸 주문");
        DomainValues.required(cancelled, "지운 주문");
        DomainValues.required(halted, "멈춘 이유");
        if (strategy == null || strategy.isBlank()) {
            throw new InvalidOrderException("어느 전략이 판단했는지 적어야 한다");
        }
        judged = List.copyOf(judged);
        placed = List.copyOf(placed);
        cancelled = List.copyOf(cancelled);
    }

    /** 봇이 멈춰 있어 전략에게 묻지도 않은 사이클. */
    public static TradingCycle halted(Instant at, TradingMode mode, String reason) {
        return new TradingCycle(
                at, mode, "멈춤", List.of(), List.of(), List.of(), Optional.of(reason));
    }

    /** 한계에 걸려 나가지 못한 주문. <b>아무것도 안 한 것과 막힌 것은 다른 사실이다.</b> */
    public List<RiskVerdict.Rejected> rejected() {
        return judged.stream()
                .filter(RiskVerdict.Rejected.class::isInstance)
                .map(RiskVerdict.Rejected.class::cast)
                .toList();
    }

    /** 이 사이클에 아무 일도 없었는가. 대부분의 사이클이 그렇다. */
    public boolean quiet() {
        return judged.isEmpty() && placed.isEmpty() && cancelled.isEmpty() && halted.isEmpty();
    }
}
