package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.market.domain.TimeRange;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 구간을 앞뒤로 갈라, <b>앞에서 고른 것이 뒤에서도 서는지</b> 본다.
 *
 * <p>Phase 6 의 감도표가 남긴 문제가 이것이다. 18조합을 한 구간에서 돌려 가장 좋은 하나를
 * 집으면 그것은 측정이 아니라 고르기이고, 고른 구간에서 좋은 것은 정의상 언제나 좋다. 같은
 * 조합이 <b>보지 않은 구간</b>에서도 서는지는 다른 질문이며, 그 질문에만 답이 있다.
 *
 * <p>후보는 이미 <b>전체 시계열에서 돌린 결과</b>여야 한다. 폴드는 그 결과를 시각으로 가를 뿐
 * 다시 돌리지 않는다 — 이유는 {@link TradeWindow} 에 적혀 있다(구간마다 다시 돌리면 지표
 * 워밍업이 되살아난다).
 *
 * <p>고르기가 <b>학습 구간만</b> 본다는 것이 이 타입의 전부다. 검증 구간을 한 번이라도
 * 들여다보면 그 순간 이것은 구간 밖 검증이 아니다. 그래서 {@link #choose} 는 검증 구간을
 * 읽지 않고, 검증은 {@link #outOfSampleOf} 로 따로 꺼낸다.
 *
 * @param inSample 조합을 고르는 데 쓰는 구간
 * @param outOfSample 고른 조합의 성적을 읽는 구간. 학습 구간과 겹칠 수 없다
 * @param minimumTrades 학습 구간에 이만큼은 있어야 고를 자격이 있다
 */
public record WalkForwardFold(TimeRange inSample, TimeRange outOfSample, int minimumTrades) {

    public WalkForwardFold {
        DomainValues.required(inSample, "학습 구간");
        DomainValues.required(outOfSample, "검증 구간");
        DomainValues.atLeast(minimumTrades, 1, "최소 거래 수");
        if (outOfSample.from().isBefore(inSample.to())) {
            throw new InvalidBacktestException(
                    "검증 구간은 학습 구간 뒤에 있어야 하고 겹칠 수 없다: 학습 %s ~ %s, 검증 %s ~ %s"
                            .formatted(inSample.from(), inSample.to(),
                                    outOfSample.from(), outOfSample.to()));
        }
    }

    /**
     * 학습 구간 성적이 가장 좋은 후보. <b>고를 수 있는 후보가 없으면 비어 있다.</b>
     *
     * <p>동점이면 먼저 온 후보를 고른다. 순서가 흔들리면 같은 입력이 같은 표를 내지 않고,
     * 그러면 완료 조건("동일 파라미터 재실행 시 결과 완전 동일")이 이 절차에서 무너진다.
     */
    public Optional<BacktestResult> choose(List<BacktestResult> candidates) {
        DomainValues.required(candidates, "후보 목록");
        return candidates.stream()
                .filter(candidate -> scoreOf(candidate).isPresent())
                .max(Comparator.comparing(candidate -> scoreOf(candidate).orElseThrow()));
    }

    public TradeWindow inSampleOf(BacktestResult result) {
        return windowOf(inSample, result);
    }

    public TradeWindow outOfSampleOf(BacktestResult result) {
        return windowOf(outOfSample, result);
    }

    /**
     * 고르기의 기준값. 자격이 없으면 비어 있고, 비어 있는 후보는 비교에 참여하지 않는다.
     *
     * <p>자격을 잃는 경우가 둘이다. <b>표본이 얇으면</b> 그 성적은 전략의 성질이 아니라 몇 번의
     * 우연이다 — 로드맵이 "12건은 통계가 아니다" 라고 적은 자리를 규칙으로 올린 것이다.
     * <b>진 거래가 하나도 없으면</b> 손익비를 말할 수 없고, 그것을 무한대로 치면 표본이 얇을수록
     * 이기는 고르기가 된다.
     */
    private Optional<BigDecimal> scoreOf(BacktestResult candidate) {
        TradeWindow window = inSampleOf(candidate);
        return window.size() < minimumTrades ? Optional.empty() : window.profitFactor();
    }

    private static TradeWindow windowOf(TimeRange range, BacktestResult result) {
        DomainValues.required(result, "백테스트 결과");
        return TradeWindow.enteredWithin(
                range, result.trades(), result.spec().account().initialCapital());
    }
}
