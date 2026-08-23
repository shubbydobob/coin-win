package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 한 지표의 지금 값과, 그 값이 최근 표본에서 차지하는 위치, 그리고 <b>어느 쪽 진영인가</b>.
 *
 * <p><b>위치가 비어 있을 수 있다.</b> 표본이 모자라면 말하지 않는다 — 손익비와 청산가에서 이미
 * 두 번 세운 규칙이다. 비었을 때 {@link #isOutlier()} 는 거짓이다. <b>모르는 것은 이상치가
 * 아니다</b> — 모른다는 이유로 경고를 띄우면 그 경고는 곧 배경이 된다.
 *
 * <p><b>진영은 표본과 무관하다.</b> 펀딩비가 양수면 롱이 숏에게 낸다는 것은 표본이 하나도 없어도
 * 성립한다 — 정의이기 때문이다. 그래서 {@code side} 는 언제나 있고 {@code neutralPosition} 만
 * 표본을 탄다. 둘을 한 칸에 묶으면 표본이 모자란 동안 진영까지 사라진다.
 *
 * <p>{@code neutralPosition} 은 <b>중립점이 눈금 어디에 오는가</b>다. 현재값과 <b>같은 방식으로</b>
 * 잰 위치라(둘 다 표본 안의 중간순위) 나란히 놓아도 어긋나지 않는다. 눈금을 순위로 그려 놓고
 * 가운데만 선형으로 찍으면 점과 선이 다른 좌표계에 있게 된다.
 */
public record MetricOutlier(
        MetricKind kind,
        BigDecimal current,
        Optional<Percentile> position,
        Optional<BigDecimal> change,
        CrowdedSide side,
        Optional<Percentile> neutralPosition,
        List<BigDecimal> samples) {

    public MetricOutlier {
        DomainValues.required(kind, "지표");
        DomainValues.required(current, "현재값");
        DomainValues.required(position, "표본 내 위치");
        DomainValues.required(change, "변화");
        DomainValues.required(side, "진영");
        DomainValues.required(neutralPosition, "중립점 위치");
        samples = List.copyOf(DomainValues.required(samples, "표본"));
    }

    /** 표본에서 현재값의 위치를 재어 만든다. 표본이 모자라면 위치가 비어 있다. */
    public static MetricOutlier of(MetricKind kind, BigDecimal current, MetricHistory history) {
        DomainValues.required(kind, "지표");
        DomainValues.required(history, "표본");
        return new MetricOutlier(
                kind,
                current,
                history.positionOf(current, kind.minimumSamples()),
                history.changeOver(kind.recentWindow(), kind.change()),
                CrowdedSide.of(kind, current),
                neutralPositionIn(kind, history),
                history.samples());
    }

    public int sampleCount() {
        return samples.size();
    }

    public boolean isOutlier() {
        return position.map(Percentile::isOutlier).orElse(false);
    }

    /**
     * 중립점이 표본 눈금의 어디인가. 축이 없거나 표본이 모자라면 비어 있다.
     *
     * <p>중립점이 표본 밖이면 위치가 0 이나 1 로 붙는다. <b>그것도 사실이다</b> — 표본 내내
     * 한쪽이었다는 뜻이고, 눈금 끝에 붙은 가운데선이 그 사실을 그대로 그린다.
     */
    private static Optional<Percentile> neutralPositionIn(MetricKind kind, MetricHistory history) {
        return kind.neutral()
                .flatMap(neutral -> history.positionOf(neutral, kind.minimumSamples()));
    }
}
