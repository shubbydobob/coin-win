package com.coinwin.position.domain;

import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;

/**
 * 분할 진입 계획 전체. 순서는 체결 순서다.
 *
 * <p>가중 평단 계산이 이 클래스의 존재 이유다. {@code n} 건까지만 체결된 상태의 평단과
 * 전량 체결 상태의 평단이 다르고, 그 차이가 청산가와 최대손실의 차이로 이어진다.
 */
public record EntryLadder(List<PlannedEntry> entries) {

    private static final BigDecimal WHOLE = new BigDecimal("100");

    public EntryLadder {
        PositionValues.required(entries, "분할 진입 계획");
        if (entries.isEmpty()) {
            throw new InvalidPositionPlanException("분할 진입 계획은 최소 1건이어야 한다");
        }
        entries = List.copyOf(entries);
        BigDecimal sum = sumAllocations(entries);
        if (sum.compareTo(WHOLE) != 0) {
            throw new InvalidPositionPlanException(
                    "분할 비중의 합은 정확히 100%% 여야 한다: %s%%".formatted(sum.toPlainString()));
        }
    }

    public static EntryLadder of(PlannedEntry... entries) {
        return new EntryLadder(List.of(entries));
    }

    public int size() {
        return entries.size();
    }

    /**
     * {@code filledCount} 건까지 체결됐을 때의 가중 평균 진입가.
     *
     * <p>가중합과 가중치 합을 {@code BigDecimal} 로 끝까지 끌고 간 뒤 마지막에 한 번만
     * {@link Price} 로 정규화한다. 항마다 스케일 2 로 반올림하면 오차가 누적된다.
     */
    public Price averagePriceAfter(int filledCount) {
        List<PlannedEntry> filled = filled(filledCount);
        BigDecimal weighted = BigDecimal.ZERO;
        for (PlannedEntry entry : filled) {
            weighted = weighted.add(entry.price().value().multiply(entry.allocation().value()));
        }
        return Price.of(weighted.divide(sumAllocations(filled), MathContext.DECIMAL64));
    }

    /** {@code filledCount} 건까지 체결됐을 때 채워진 비중. 전량이면 100%. */
    public Percentage allocationFilledAfter(int filledCount) {
        return Percentage.of(sumAllocations(filled(filledCount)));
    }

    public Price lowestPrice() {
        return entries.stream().map(PlannedEntry::price)
                .min(Comparator.comparing(Price::value)).orElseThrow();
    }

    public Price highestPrice() {
        return entries.stream().map(PlannedEntry::price)
                .max(Comparator.comparing(Price::value)).orElseThrow();
    }

    private List<PlannedEntry> filled(int filledCount) {
        if (filledCount < 1 || filledCount > entries.size()) {
            throw new InvalidValueException(
                    "체결 건수는 1 이상 %d 이하여야 한다: %d".formatted(entries.size(), filledCount));
        }
        return entries.subList(0, filledCount);
    }

    private static BigDecimal sumAllocations(List<PlannedEntry> entries) {
        return entries.stream()
                .map(entry -> entry.allocation().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
