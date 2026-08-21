package com.coinwin.ai.adapter.out.openai;

import com.coinwin.ai.domain.DraftedEntry;
import com.coinwin.ai.domain.DraftedFields;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * 모델이 돌려주는 JSON 의 모양. <b>전부 비어 있을 수 있다.</b>
 *
 * <p>도메인이 아니라 어댑터에 사는 이유는 이 타입이 <b>모델의 어휘</b>이기 때문이다.
 * 방향이 문자열인 것도 그래서다 — 열거형으로 받으면 모델이 "롱" 이라고 답한 순간 역직렬화가
 * 깨지고, 깨진 예외는 "방향을 못 읽었다" 보다 훨씬 못한 정보를 준다.
 *
 * <p>스케일 정규화는 값 객체가 한다. 여기서는 {@code BigDecimal} 그대로 받아 넘기기만 한다.
 */
record DraftedPlanResponse(
        String direction,
        List<DraftedEntryResponse> entries,
        BigDecimal stopLoss,
        BigDecimal takeProfit,
        Integer leverage) {

    record DraftedEntryResponse(BigDecimal price, BigDecimal allocation) {

        DraftedEntry toEntry() {
            return new DraftedEntry(toPrice(price), toPercentage(allocation));
        }
    }

    DraftedFields toFields() {
        return new DraftedFields(toDirection(), toEntries(), toPrice(stopLoss),
                toPrice(takeProfit), leverage);
    }

    private Direction toDirection() {
        if (direction == null) {
            return null;
        }
        return switch (direction.strip().toUpperCase(Locale.ROOT)) {
            case "LONG" -> Direction.LONG;
            case "SHORT" -> Direction.SHORT;
            // 모델이 규칙을 어기고 다른 말을 했다. 지어내는 것보다 못 읽은 것으로 두는 편이 낫다.
            default -> null;
        };
    }

    private List<DraftedEntry> toEntries() {
        if (entries == null) {
            return null;
        }
        return entries.stream()
                .map(entry -> entry == null ? null : entry.toEntry())
                .toList();
    }

    private static Price toPrice(BigDecimal value) {
        return value == null ? null : Price.of(value);
    }

    private static Percentage toPercentage(BigDecimal value) {
        return value == null ? null : Percentage.of(value);
    }
}
