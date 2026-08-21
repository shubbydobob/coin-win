package com.coinwin.ai.domain;

import com.coinwin.common.domain.Price;
import com.coinwin.position.domain.Direction;
import com.coinwin.position.domain.EntryLadder;
import com.coinwin.position.domain.PlannedEntry;
import com.coinwin.position.domain.PositionPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 모델이 문장에서 읽어 온 칸들. <b>이 타입만이 빈 칸을 안다.</b>
 *
 * <p>{@link #complete()} 를 지나면 계획은 완전하거나 존재하지 않는다. 그 경계를 한 곳에 두는
 * 것이 이 클래스의 전부다 — 여러 곳에서 null 을 다루면 그중 한 곳이 언젠가 기본값을 채운다.
 *
 * <p>칸이 다 찼다고 계획이 성립하는 것은 아니다. 비중 합·손절가 방향 같은 정합성은
 * {@link EntryLadder} 와 {@link PositionPlan} 이 이미 갖고 있고 여기서 다시 쓰지 않는다.
 * 규칙을 두 벌 두면 언젠가 한쪽만 바뀐다.
 */
public record DraftedFields(
        Direction direction,
        List<DraftedEntry> entries,
        Price stopLoss,
        Price takeProfit,
        Integer leverage) {

    public DraftedFields {
        // List.copyOf 를 쓰지 않는다 — 원소가 null 이면 던지는데, 원소가 null 인 것은
        // 여기서 거부할 오류가 아니라 missing() 이 답해야 할 상태다.
        entries = entries == null ? null : Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** 빠진 칸. 순서는 {@link PlanField} 선언 순서이므로 같은 입력은 같은 목록을 낸다. */
    public List<PlanField> missing() {
        return Stream.of(
                        missingIf(PlanField.DIRECTION, direction == null),
                        missingIf(PlanField.ENTRIES, entriesAreBlank()),
                        missingIf(PlanField.STOP_LOSS, stopLoss == null),
                        missingIf(PlanField.TAKE_PROFIT, takeProfit == null),
                        missingIf(PlanField.LEVERAGE, leverage == null))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * 계획으로 굳힌다.
     *
     * @throws IncompletePlanException 칸이 하나라도 비어 있는 경우
     */
    public PositionPlan complete() {
        List<PlanField> missing = missing();
        if (!missing.isEmpty()) {
            throw new IncompletePlanException(missing);
        }
        return new PositionPlan(direction, ladder(), stopLoss, takeProfit, leverage);
    }

    /**
     * 한 회차라도 비어 있으면 진입 계획 전체가 빠진 것으로 본다.
     *
     * <p>가격 없는 회차를 빼고 나머지로 만들면 비중 합이 100 이 아니게 되거나, 더 나쁘게는
     * 우연히 100 이 되어 사용자가 의도하지 않은 계획이 조용히 성립한다.
     */
    private boolean entriesAreBlank() {
        return entries == null || entries.isEmpty()
                || entries.stream().anyMatch(entry -> entry == null || entry.isBlank());
    }

    private EntryLadder ladder() {
        return new EntryLadder(entries.stream()
                .map(entry -> new PlannedEntry(entry.price(), entry.allocation()))
                .toList());
    }

    private static Optional<PlanField> missingIf(PlanField field, boolean blank) {
        return blank ? Optional.of(field) : Optional.empty();
    }
}
