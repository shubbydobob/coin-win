package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 한 지표의 시계열 표본. 펀딩비·미결제약정·롱숏비율이 각각 하나씩 갖는다.
 *
 * <p>하는 일은 둘뿐이다 — 표본을 들고 있는 것과, <b>표본이 모자라면 위치를 내지 않는 것</b>.
 * 뒤쪽이 이 타입이 존재하는 이유다. {@code Percentile.of} 는 표본이 하나만 있어도 답을 내므로,
 * "충분한가" 를 묻는 자리가 따로 필요하다.
 *
 * <p>"표본 3개 중 상위 33%" 는 수치의 모양만 갖춘 거짓말이다. 손익비를 말할 수 없을 때 0 을
 * 적지 않고, 거래소가 청산가를 말할 수 없을 때 0 원짜리 청산가를 담지 않은 것과 같은 규칙이다.
 *
 * <p>최소 표본 수를 <b>인자로 받는다.</b> 지표마다 다르기 때문이다 — 펀딩비는 8시간 주기라
 * 30개가 열흘이고, 미결제약정은 5분 주기라 20개가 100분이다. 그 수는 이 타입이 아니라 부르는
 * 쪽의 정책이다.
 */
public record MetricHistory(List<BigDecimal> samples) {

    /** 변화율의 자릿수. 0.0001 = 0.01% 까지 구분된다. */
    private static final int CHANGE_SCALE = 6;

    public MetricHistory {
        samples = List.copyOf(DomainValues.required(samples, "표본"));
    }

    /**
     * 표본 안에서 현재값의 위치. 표본이 {@code minimumSamples} 에 못 미치면 비어 있다.
     *
     * <p>빈 표본은 예외가 아니다 — 거래소가 이력을 아직 안 주는 것은 정상적인 상태이고,
     * 그때 화면은 그 칸만 비우면 된다.
     */
    public Optional<Percentile> positionOf(BigDecimal current, int minimumSamples) {
        DomainValues.atLeast(minimumSamples, 1, "최소 표본 수");
        if (samples.size() < minimumSamples) {
            return Optional.empty();
        }
        return Optional.of(Percentile.of(current, samples));
    }


    /**
     * 최근 {@code window} 개 구간의 변화. 창의 <b>첫 값 대비</b>다.
     *
     * <p>표본이 창보다 적으면 비어 있다 — 두 점이 없으면 변화라는 것이 성립하지 않는다.
     *
     * <p>{@code RATIO} 는 비율(0.032 = 3.2% 증가), {@code DIFFERENCE} 는 차이(%p)다.
     * 부호가 바뀌는 값에서 비율이 무너지기 때문이고, 그 판단은 {@link MetricKind} 가 갖는다.
     *
     * <p><b>0 에서 출발한 비율은 내지 않는다.</b> 분모가 0 이면 몇 배가 됐는지 말할 수 없다 —
     * 무한대를 큰 수로 적으면 화면이 그것을 급변으로 읽는다.
     */
    public Optional<BigDecimal> changeOver(int window, MetricKind.Change kind) {
        DomainValues.atLeast(window, 2, "변화의 창");
        DomainValues.required(kind, "변화 표기");
        if (samples.size() < window) {
            return Optional.empty();
        }
        BigDecimal first = samples.get(samples.size() - window);
        BigDecimal last = samples.getLast();
        if (kind == MetricKind.Change.DIFFERENCE) {
            return Optional.of(last.subtract(first));
        }
        if (first.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(last.subtract(first)
                .divide(first.abs(), CHANGE_SCALE, RoundingMode.HALF_UP));
    }

    /** 가장 최근 값. 표본이 비어 있으면 없다. */
    public Optional<BigDecimal> latest() {
        return samples.isEmpty() ? Optional.empty() : Optional.of(samples.getLast());
    }

    public int sampleCount() {
        return samples.size();
    }
}
