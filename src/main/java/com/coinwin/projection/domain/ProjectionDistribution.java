package com.coinwin.projection.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.util.Comparator;
import java.util.List;

/**
 * N 번의 시행이 만든 결과 분포.
 *
 * <p>이 타입이 답하는 질문은 "평균이 얼마인가" 가 아니라 <b>"운이 나쁘면 어디까지 가는가"</b> 다.
 * 기댓값이 같아도 하위 5% 와 상위 5% 가 몇 배로 갈리고, 그 폭이 곧 감당해야 할 몫이다.
 */
public record ProjectionDistribution(List<ProjectionOutcome> outcomes, Money initialCapital) {

    private static final int LOWEST = 0;
    private static final int HIGHEST = 100;

    public ProjectionDistribution {
        DomainValues.required(outcomes, "시행 결과");
        DomainValues.required(initialCapital, "초기 자본");
        if (outcomes.isEmpty()) {
            throw new InvalidProjectionException("시행이 한 번도 없는 분포는 성립하지 않는다");
        }
        outcomes = List.copyOf(outcomes);
    }

    public int runs() {
        return outcomes.size();
    }

    /**
     * 최종 자산의 백분위. 0 은 최악, 50 은 중앙값, 100 은 최선이다.
     *
     * <p>최근접 순위법이다 — 보간하지 않고 <b>실제로 일어난 시행 하나</b>를 가리킨다.
     * 보간한 값은 어느 시행에서도 일어나지 않은 숫자라 "이런 결과가 나왔다" 고 말할 수 없다.
     */
    public Money equityPercentile(int percentile) {
        return rankedAt(Comparator.comparing(outcome -> outcome.finalEquity().value()), percentile)
                .finalEquity();
    }

    /** 최대낙폭의 백분위. 최종 자산과 <b>따로</b> 정렬한다 — 잘 번 시행이 얌전히 갔다는 보장은 없다. */
    public Percentage drawdownPercentile(int percentile) {
        return rankedAt(Comparator.comparing(outcome -> outcome.maxDrawdown().value()), percentile)
                .maxDrawdown();
    }

    /** 최종 자산이 초기 자본에 못 미친 시행의 비율. 본전은 손실이 아니다. */
    public Percentage lossProbability() {
        long losing = outcomes.stream()
                .filter(outcome -> initialCapital.isGreaterThan(outcome.finalEquity()))
                .count();
        return Percentage.ofRatio(losing, runs());
    }

    private ProjectionOutcome rankedAt(Comparator<ProjectionOutcome> order, int percentile) {
        if (percentile < LOWEST || percentile > HIGHEST) {
            throw new InvalidValueException("백분위는 0 과 100 사이여야 한다: " + percentile);
        }
        List<ProjectionOutcome> ranked = outcomes.stream().sorted(order).toList();
        return ranked.get(nearestRank(percentile, ranked.size()));
    }

    /** {@code ceil(p/100 × n) - 1} 을 정수 연산으로 쓴 것이다. 짝수 시행의 중앙값은 아래쪽이 된다. */
    private static int nearestRank(int percentile, int size) {
        return Math.max((percentile * size + HIGHEST - 1) / HIGHEST - 1, 0);
    }
}
