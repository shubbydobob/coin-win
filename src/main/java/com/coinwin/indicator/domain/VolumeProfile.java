package com.coinwin.indicator.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.CandleSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 어느 가격에서 얼마나 오갔는가 — 매물대.
 *
 * <p><b>지지·저항대와 다른 것을 잰다.</b> 대({@code PriceZone})는 <b>가격이 몇 번 되돌아섰나</b>
 * 를 세고, 매물대는 <b>거기서 얼마나 많이 거래됐나</b>를 센다. 같은 자리를 가리킬 때도 있지만
 * 근거가 다르므로 화면에 나란히 놓는다 — 둘이 겹치면 그것이 그 자리에 대한 두 개의 증거다.
 *
 * <p><b>이 타입은 방향을 말하지 않는다.</b> 위의 매물대가 저항이라는 것은 해석이고, 이 저장소는
 * "대에 닿으면 되돌아온다" 는 비슷한 전제를 7년 데이터로 반증했다({@code docs/adr/021}).
 * 담는 것은 위치와 두께까지다.
 *
 * <p><b>검증되지 않았다.</b> 일목·볼린저는 트레이딩뷰 원문과 대조했고 대는 15,110봉으로
 * 돌렸는데, 매물대는 아직 어떤 백테스트도 통과한 적이 없다. 화면에 사실로 놓되 검증된 것과
 * 같은 무게로 읽히지 않게 해야 한다.
 */
public record VolumeProfile(List<VolumeBin> bins, VolumeProfileSettings settings) {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    public VolumeProfile {
        DomainValues.required(bins, "가격 구간");
        DomainValues.required(settings, "매물대 설정");
        if (bins.isEmpty()) {
            throw new InvalidIndicatorException("매물대는 최소 한 구간이어야 한다");
        }
        bins = List.copyOf(bins);
    }

    /**
     * 캔들에서 매물대를 만든다.
     *
     * <p><b>워밍업이 없다.</b> 일목이 52봉, 볼린저가 20봉을 요구하는 것과 달리 이것은 첫 봉부터
     * 값을 낸다 — 이동 평균이 아니라 합계이기 때문이다. 다만 봉이 적으면 칸마다 표본이 얇아
     * 매물대가 우연히 정해진다.
     */
    public static VolumeProfile over(CandleSeries series, VolumeProfileSettings settings) {
        DomainValues.required(series, "캔들");
        DomainValues.required(settings, "매물대 설정");
        if (series.isEmpty()) {
            throw new InsufficientCandlesException("매물대", 1, 0);
        }
        return new VolumeProfile(VolumeBins.over(series, settings.bins()), settings);
    }

    /** 칸들의 거래량 합. 나눠 담으면서 생긴 반올림 먼지 때문에 원본 합과 미세하게 다를 수 있다. */
    public Quantity totalVolume() {
        return bins.stream().map(VolumeBin::volume).reduce(Quantity::plus).orElseThrow();
    }

    /**
     * 가장 두꺼운 칸 하나. 흔히 POC 라 부른다.
     *
     * <p>동점이면 아래쪽을 고른다. 어느 쪽을 골라도 임의적이지만 <b>고르는 방식이 정해져
     * 있어야</b> 같은 캔들에서 같은 답이 나온다 — 백테스트 결정론과 같은 요구다.
     */
    public VolumeBin pointOfControl() {
        return bins.stream()
                .max(Comparator.comparing((VolumeBin bin) -> bin.volume().value())
                        .thenComparing(bin -> bin.band().lower().value(), Comparator.reverseOrder()))
                .orElseThrow();
    }

    /**
     * 평균보다 두꺼운 칸들. <b>붙어 있으면 한 덩이로 합친다.</b>
     *
     * <p>합치지 않으면 나란한 칸 셋이 매물대 셋으로 세어지고, 화면은 "가까운 매물대 세 개" 를
     * 보여 준다 — 실제로는 폭이 넓은 하나다. 대를 군집으로 묶는 {@code ZoneMap} 과 같은 판단이다.
     *
     * <p>가격 오름차순이다.
     */
    public List<VolumeShelf> shelves() {
        BigDecimal threshold = threshold();
        List<VolumeShelf> shelves = new ArrayList<>();
        List<VolumeBin> run = new ArrayList<>();
        for (VolumeBin bin : bins) {
            if (bin.volume().value().compareTo(threshold) >= 0) {
                run.add(bin);
            } else if (!run.isEmpty()) {
                shelves.add(merge(run));
                run = new ArrayList<>();
            }
        }
        if (!run.isEmpty()) {
            shelves.add(merge(run));
        }
        return List.copyOf(shelves);
    }

    /** 지금 가격보다 아래에서 가장 가까운 매물대. 가격을 품은 것은 위도 아래도 아니다. */
    public Optional<VolumeShelf> nearestBelow(Price price) {
        DomainValues.required(price, "현재가");
        return shelves().stream()
                .filter(shelf -> !shelf.band().upper().isAbove(price))
                .reduce((first, second) -> second);
    }

    /** 지금 가격보다 위에서 가장 가까운 매물대. */
    public Optional<VolumeShelf> nearestAbove(Price price) {
        DomainValues.required(price, "현재가");
        return shelves().stream()
                .filter(shelf -> !shelf.band().lower().isBelow(price))
                .findFirst();
    }

    /**
     * 지금 가격을 품고 있는 매물대.
     *
     * <p><b>이것이 비어 있지 않다는 사실 자체가 정보다.</b> 없으면 위아래가 둘 다 비어 보이는데,
     * 그것은 "매물대가 없다" 와 "지금 매물대 한가운데에 있다" 를 같은 화면으로 만든다.
     */
    public Optional<VolumeShelf> containing(Price price) {
        DomainValues.required(price, "현재가");
        return shelves().stream()
                .filter(shelf -> shelf.band().positionOf(price) == BandPosition.INSIDE)
                .findFirst();
    }

    private BigDecimal threshold() {
        return totalVolume().value()
                .divide(BigDecimal.valueOf(bins.size()), PRECISION)
                .multiply(settings.heavyMultiple());
    }

    private VolumeShelf merge(List<VolumeBin> run) {
        Quantity volume = run.stream().map(VolumeBin::volume).reduce(Quantity::plus).orElseThrow();
        return new VolumeShelf(
                new PriceBand(run.getLast().band().upper(), run.getFirst().band().lower()),
                volume,
                share(volume));
    }

    private Percentage share(Quantity volume) {
        BigDecimal total = totalVolume().value();
        return total.signum() == 0
                ? Percentage.of(BigDecimal.ZERO)
                : Percentage.of(volume.value()
                        .multiply(new BigDecimal("100"))
                        .divide(total, PRECISION));
    }
}
