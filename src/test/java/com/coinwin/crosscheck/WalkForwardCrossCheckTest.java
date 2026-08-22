package com.coinwin.crosscheck;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.backtest.domain.BacktestResult;
import com.coinwin.backtest.domain.TradeWindow;
import com.coinwin.backtest.domain.WalkForwardFold;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.TimeRange;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 앞 구간에서 고른 조합이 <b>보지 않은 뒤 구간</b>에서도 서는지 본다.
 *
 * <p>감도표가 답하지 못하는 질문이 이것이다. 18조합을 한 구간에서 돌려 가장 좋은 하나를 집으면
 * 그것은 측정이 아니라 고르기이고, 고른 구간에서 좋은 것은 정의상 언제나 좋다. 로드맵이
 * "한 칸 바꿔서 결과가 뒤집히는 파라미터는 전략이 아니라 잡음을 고르고 있다" 고 적은 것을
 * <b>절차로 만든 것</b>이다.
 *
 * <p>학습 구간은 <b>처음부터 그 해 직전까지</b> 누적한다(anchored). 매년 "지금까지의 전부를
 * 보고 하나를 골랐다면 내년에 어땠을까" 를 묻는 것이고, 그것이 실제로 이 도구를 쓰게 되는
 * 방식이다.
 *
 * <p>전체 시계열에서 <b>조합마다 한 번씩만</b> 돌린다. 폴드마다 캔들을 잘라 다시 돌리면 지표
 * 워밍업이 폴드마다 되살아나 각 폴드 앞부분의 거래가 통째로 사라진다 — 근거는
 * {@link TradeWindow} 에 적혀 있다.
 *
 * <p>기본 {@code test} 에서 제외한다. 실행은 {@code .\gradlew.bat crossCheck} 다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=30s"
        })
class WalkForwardCrossCheckTest {

    /**
     * 학습 구간에 이만큼은 있어야 고를 자격이 있다.
     *
     * <p>로드맵이 "12건은 통계가 아니다" 라고 적었다. 그보다 확실히 큰 수를 골랐다 — 30 이
     * 통계로 충분하다는 뜻이 아니라, 이 아래는 확실히 아니라는 뜻이다.
     */
    private static final int MIN_TRADES = 30;

    private static final int FIRST_VERIFIED_YEAR = 2022;
    private static final int LAST_VERIFIED_YEAR = 2026;

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 해마다_직전까지로_고른_조합의_다음_해_성적을_출력한다() {
        List<BacktestResult> candidates = runGrid();

        System.out.printf("%n=== 구간 밖 검증 (anchored walk-forward, 4h) ===%n");
        System.out.printf("학습: 상장 ~ 그 해 직전 | 검증: 그 해 | 최소 학습 거래 %d건%n",
                MIN_TRADES);

        int verified = 0;
        for (int year = FIRST_VERIFIED_YEAR; year <= LAST_VERIFIED_YEAR; year++) {
            verified += printFold(candidates, year) ? 1 : 0;
        }

        printGuide();
        assertThat(verified).isPositive();
    }

    /** 폴드 한 해. 고를 수 있었는지, 골랐다면 그 조합이 다음 해에 어땠는지. */
    private static boolean printFold(List<BacktestResult> candidates, int year) {
        TimeRange verification = CrossCheckSupport.calendarYear(year);
        WalkForwardFold fold = new WalkForwardFold(
                new TimeRange(CrossCheckSupport.HISTORY_FROM, verification.from()),
                verification, MIN_TRADES);

        Optional<BacktestResult> chosen = fold.choose(candidates);
        System.out.printf("%n── %d 년 ──%n", year);
        if (chosen.isEmpty()) {
            System.out.printf("고를 수 있는 조합이 없다 (학습 구간 %d건 미만이거나 진 거래 0)%n",
                    MIN_TRADES);
            return false;
        }

        BacktestResult winner = chosen.orElseThrow();
        CrossCheckSupport.header("고른 조합 " + CrossCheckSupport.labelOf(winner.spec()));
        CrossCheckSupport.row("학습 (~ " + year + ")", fold.inSampleOf(winner));
        CrossCheckSupport.row("검증 (" + year + ")", fold.outOfSampleOf(winner));
        return true;
    }

    private List<BacktestResult> runGrid() {
        CandleSeries series = CrossCheckSupport.history(
                binanceRestClient, properties, CandleInterval.FOUR_HOURS);
        return CrossCheckSupport.grid().stream()
                .map(combination -> CrossCheckSupport.ENGINE.run(combination.spec(), series))
                .toList();
    }

    private static void printGuide() {
        System.out.printf("%n── 이 표를 어떻게 읽는가 ──%n");
        System.out.println("학습 줄과 검증 줄의 손익비를 나란히 본다. 학습에서 좋은 것은");
        System.out.println("당연하다 — 그 구간을 보고 고른 것이기 때문이다. 물어보는 것은");
        System.out.println("검증 줄이 1 을 넘는가, 그리고 해마다 같은 조합이 골라지는가 둘이다.");
        System.out.println("해마다 다른 조합이 골라진다면 그 격자에는 안정된 최적점이 없다.");
    }
}
