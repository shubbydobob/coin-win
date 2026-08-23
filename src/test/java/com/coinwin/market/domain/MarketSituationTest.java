package com.coinwin.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 두 수가 함께 움직였을 때 기계적으로 성립하는 사실.
 *
 * <p><b>이 스위트가 지키는 것은 무엇이 뜨는가보다 무엇이 뜨지 않는가다.</b> 문장이 늘 떠
 * 있으면 그것은 배경이 되고, 배경이 된 경고는 아무것도 경고하지 않는다.
 */
class MarketSituationTest {

    /**
     * 계기가 된 사건의 모양이다. 포지션이 <b>줄면서</b> 가격이 올랐다면 그 움직임은 새 돈이
     * 아니라 청산이 만든 것이다.
     */
    @Test
    void 미결제약정이_줄면서_가격이_오르면_정리로_읽는다() {
        List<MarketSituation> found = 상황(미결제약정("100", "97"), 가격("76000", "77000"));

        assertThat(found).extracting(MarketSituation::kind)
                .contains(MarketSituation.Kind.SHORT_UNWIND);
        assertThat(found).extracting(MarketSituation::description)
                .anySatisfy(문장 -> assertThat(문장).contains("포지션이 정리되며"));
    }

    @Test
    void 미결제약정이_줄면서_가격이_내리면_반대쪽_정리다() {
        assertThat(상황(미결제약정("100", "97"), 가격("77000", "76000")))
                .extracting(MarketSituation::kind)
                .contains(MarketSituation.Kind.LONG_UNWIND);
    }

    @Test
    void 미결제약정이_늘면서_가격이_움직이면_새_포지션이다() {
        assertThat(상황(미결제약정("100", "103"), 가격("76000", "77000")))
                .extracting(MarketSituation::kind)
                .contains(MarketSituation.Kind.BUILDING_UP);
        assertThat(상황(미결제약정("100", "103"), 가격("77000", "76000")))
                .extracting(MarketSituation::kind)
                .contains(MarketSituation.Kind.BUILDING_DOWN);
    }

    /** <b>뜨지 않는 것이 기본값이다.</b> 조용한 시장에서는 아무 문장도 없어야 한다. */
    @Test
    void 둘_다_거의_안_움직이면_아무_문장도_없다() {
        assertThat(상황(미결제약정("100", "100.2"), 가격("76000", "76010"))).isEmpty();
    }

    /** 포지션이 크게 움직여도 <b>가격이 조용하면</b> 말할 것이 없다. 짝이 되어야 사실이 된다. */
    @Test
    void 가격이_조용하면_포지션만으로는_말하지_않는다() {
        assertThat(상황(미결제약정("100", "90"), 가격("76000", "76050"))).isEmpty();
    }

    @Test
    void 가격이_움직여도_포지션이_조용하면_말하지_않는다() {
        assertThat(상황(미결제약정("100", "100.3"), 가격("76000", "77500"))).isEmpty();
    }

    @Test
    void 펀딩비가_한쪽으로_기울면_비용을_말한다() {
        MetricOutlier 펀딩 = MetricOutlier.of(
                MetricKind.FUNDING_RATE, new BigDecimal("0.05"), 표본("0.01", "0.03", "0.05"));

        assertThat(MarketSituation.of(List.of(펀딩), 가격("76000", "76010")))
                .extracting(MarketSituation::description)
                .anySatisfy(문장 -> assertThat(문장).contains("롱이 숏에게"));
    }

    @Test
    void 펀딩비가_평범하면_문장이_없다() {
        MetricOutlier 펀딩 = MetricOutlier.of(
                MetricKind.FUNDING_RATE, new BigDecimal("0.01"), 표본("0.01", "0.01", "0.01"));

        assertThat(MarketSituation.of(List.of(펀딩), 가격("76000", "76010"))).isEmpty();
    }

    /**
     * <b>어떤 문장도 사용자에게 무엇을 하라고 말하지 않는다.</b> 이 검사가 규칙 1 을 코드로
     * 세운 것이다 — 문장이 늘 때마다 여기서 걸린다.
     */
    @Test
    void 어떤_문장도_행동을_지시하지_않는다() {
        List<String> 모든문장 = Stream.of(
                        상황(미결제약정("100", "97"), 가격("76000", "77000")),
                        상황(미결제약정("100", "97"), 가격("77000", "76000")),
                        상황(미결제약정("100", "103"), 가격("76000", "77000")),
                        상황(미결제약정("100", "103"), 가격("77000", "76000")))
                .flatMap(List::stream)
                .map(MarketSituation::description)
                .toList();

        assertThat(모든문장).isNotEmpty().allSatisfy(문장 -> assertThat(문장)
                .doesNotContain("사라", "팔아", "진입", "청산하라", "따라", "매수", "매도"));
    }

    private static List<MarketSituation> 상황(MetricOutlier openInterest, MetricOutlier price) {
        return MarketSituation.of(List.of(openInterest), price);
    }

    private static MetricOutlier 미결제약정(String... values) {
        return MetricOutlier.of(
                MetricKind.OPEN_INTEREST, new BigDecimal(values[values.length - 1]), 표본(values));
    }

    private static MetricOutlier 가격(String... values) {
        return MetricOutlier.of(
                MetricKind.PRICE, new BigDecimal(values[values.length - 1]), 표본(values));
    }

    /** 창(6)을 채우려면 여섯 개가 필요하다. 앞을 첫 값으로 메운다. */
    private static MetricHistory 표본(String... values) {
        List<BigDecimal> 채운것 = Stream.concat(
                        Stream.generate(() -> new BigDecimal(values[0])).limit(6 - values.length),
                        Stream.of(values).map(BigDecimal::new))
                .toList();
        return new MetricHistory(채운것);
    }
}
