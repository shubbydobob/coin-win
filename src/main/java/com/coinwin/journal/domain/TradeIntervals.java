package com.coinwin.journal.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 거래와 거래 사이의 간격. 직전 청산부터 다음 진입까지다.
 *
 * <p>손익만 쌓으면 보이지 않는 것이 있다 — <b>손실 직후 곧바로 다시 들어가는 습관</b>이다.
 * 그것은 개별 거래의 속성이 아니라 거래 사이의 속성이라서, 간격을 따로 세지 않으면 어떤
 * 집계에도 나타나지 않는다.
 *
 * <p>간격의 개수는 거래 수보다 하나 적다. 거래가 1건이면 간격이 없다.
 */
public record TradeIntervals(int gaps, Duration shortest, Duration average, int overlaps) {

    public static TradeIntervals none() {
        return new TradeIntervals(0, Duration.ZERO, Duration.ZERO, 0);
    }

    /**
     * 시간순으로 정렬된 거래 목록의 간격.
     *
     * <p><b>겹치는 쌍은 간격에서 빼고 그 수를 따로 센다.</b> 앞 거래가 닫히기 전에 뒤 거래가
     * 열렸다면 그 둘 사이의 "간격" 은 존재하지 않는다. 음수를 그대로 평균에 넣으면 간격이
     * 실제보다 짧아 보이고, 그렇다고 집계 전체를 실패시키면 기록 하나가 어긋났다는 이유로
     * 나머지 수치까지 볼 수 없게 된다. {@code overlaps} 가 0 이 아니면 눈에 띈다.
     *
     * <p>간격 계산 자체는 {@link ClosedTrade#timeSincePreviousTrade} 에 맡긴다. 여기서 뺄셈을
     * 다시 하면 경계 규칙이 두 곳에 생긴다.
     */
    static TradeIntervals over(List<ClosedTrade> chronological) {
        List<Duration> gaps = new ArrayList<>();
        int overlaps = 0;
        for (int index = 1; index < chronological.size(); index++) {
            ClosedTrade current = chronological.get(index);
            ClosedTrade previous = chronological.get(index - 1);
            if (current.opensAfter(previous)) {
                gaps.add(current.timeSincePreviousTrade(previous));
            } else {
                overlaps++;
            }
        }
        return summarize(gaps, overlaps);
    }

    private static TradeIntervals summarize(List<Duration> gaps, int overlaps) {
        if (gaps.isEmpty()) {
            return new TradeIntervals(0, Duration.ZERO, Duration.ZERO, overlaps);
        }
        Duration total = gaps.stream().reduce(Duration.ZERO, Duration::plus);
        return new TradeIntervals(
                gaps.size(), Collections.min(gaps), total.dividedBy(gaps.size()), overlaps);
    }

    public boolean isEmpty() {
        return gaps == 0;
    }
}
