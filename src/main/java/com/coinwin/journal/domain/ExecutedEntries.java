package com.coinwin.journal.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/**
 * 진입 체결 내역 전체. 순서가 체결 순서다.
 *
 * <p>평단 계산이 이 클래스의 존재 이유다. {@code EntryLadder} 의 평단은 <b>비중</b> 가중이고
 * 이쪽은 <b>수량</b> 가중이다 — 계획에는 수량이 없고 체결에는 비중이 없기 때문에 두 계산은
 * 합쳐지지 않는다. 대신 형태는 같게 맞춰 두었다: {@code BigDecimal} 로 끝까지 끌고 간 뒤
 * 마지막에 한 번만 {@link Price} 로 정규화한다. 항마다 스케일 2 로 스냅하면 오차가 쌓인다.
 */
public record ExecutedEntries(List<Fill> fills) {

    public ExecutedEntries {
        DomainValues.required(fills, "진입 체결 내역");
        if (fills.isEmpty()) {
            throw new InvalidTradeException("진입 체결 내역은 최소 1건이어야 한다");
        }
        fills = List.copyOf(fills);
        assertChronological(fills);
    }

    public static ExecutedEntries of(Fill... fills) {
        return new ExecutedEntries(List.of(fills));
    }

    public int count() {
        return fills.size();
    }

    /** 수량 가중 평균 진입가. 손익과 반사실 손실이 모두 이 값에서 나온다. */
    public Price averagePrice() {
        BigDecimal weighted = BigDecimal.ZERO;
        for (Fill fill : fills) {
            weighted = weighted.add(fill.price().value().multiply(fill.quantity().value()));
        }
        return Price.of(weighted.divide(totalQuantityValue(), MathContext.DECIMAL64));
    }

    public Quantity totalQuantity() {
        return Quantity.of(totalQuantityValue());
    }

    /**
     * 포지션이 열린 시각. 첫 체결이다.
     *
     * <p>진입 시각을 별도 필드로 두지 않는 이유는 그것이 체결 내역과 어긋날 수 있기
     * 때문이다. 두 곳에 적힌 같은 사실은 언젠가 갈라진다.
     */
    public Instant firstFilledAt() {
        return fills.getFirst().at();
    }

    public Instant lastFilledAt() {
        return fills.getLast().at();
    }

    private BigDecimal totalQuantityValue() {
        return fills.stream()
                .map(fill -> fill.quantity().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 정렬하지 않고 거부한다. 순서를 고쳐 주면 "1차가 먼저 체결됐다" 는 기록이 조용히
     * 바뀌고, 분할 진입에서 그 순서가 곧 평단의 변천사다. 같은 시각의 두 건은 허용한다.
     */
    private static void assertChronological(List<Fill> fills) {
        for (int index = 1; index < fills.size(); index++) {
            if (fills.get(index).at().isBefore(fills.get(index - 1).at())) {
                throw new InvalidTradeException(
                        "진입 체결은 시간 오름차순이어야 한다: %s 뒤에 %s"
                                .formatted(fills.get(index - 1).at(), fills.get(index).at()));
            }
        }
    }
}
