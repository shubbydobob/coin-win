package com.coinwin.trading.adapter.out;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.trading.domain.TradingMode;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 봇의 설정.
 *
 * <p><b>기본값이 꺼짐이다.</b> 앱을 띄운 것과 봇을 켠 것은 다른 결정이고, 기본이 켜짐이면
 * 누군가 이 저장소를 처음 실행하는 순간 루프가 돈다.
 *
 * <p><b>모드의 기본값은 장부다.</b> 실계좌는 환경변수를 명시적으로 바꿔야 하고, 그 전환이
 * 실패하면 앱이 실패한다 — 조용히 장부로 내려오지 않는다({@code scope.md}).
 *
 * @param enabled 루프가 도는가. 끄면 열린 포지션은 건드리지 않는다 — 봇을 끄는 것과 포지션을
 *     닫는 것은 다른 결정이고, 끄는 순간 전량 시장가로 닫으면 그 자체가 원하지 않은 매매다
 * @param mode 주문이 어디로 가는가
 * @param cycle 얼마나 자주 깨어나는가. <b>주기는 전략의 일부인데 전략이 없다</b> — 1분은
 *     자리표시자다
 * @param candles 전략에게 넘길 최근 봉 수
 * @param equity 장부의 시작 자산. 한계가 전부 이것의 비율이다
 * @param takerFeePercent 체결 수수료. 바이낸스 테이커 기본값
 * @param slippagePercent 미끄러짐. <b>근거 있는 수가 아니라 자리표시자다</b> — 복리 계산기가
 *     같은 값을 같은 이유로 쓴다
 * @param strategy 무엇이 판단하는가. {@code hold} 는 아무것도 하지 않고,
 *     {@code stop-guard} 는 진입하지 않고 손절만 지킨다
 * @param stopDistancePercent 손절 거리(%). <b>자리표시자다</b>
 * @param firstTargetPercent 1차 익절 거리(%). <b>자리표시자다</b>
 * @param exchangeUrl 주문을 보낼 주소. <b>기본값이 테스트넷이다</b> — 실계좌 주소는
 *     손으로 적어야 하고, 그 한 줄이 돈이 움직이는 것을 뜻한다
 */
@ConfigurationProperties("coinwin.trading")
public record TradingProperties(
        boolean enabled,
        TradingMode mode,
        Duration cycle,
        int candles,
        BigDecimal equity,
        BigDecimal takerFeePercent,
        BigDecimal slippagePercent,
        String strategy,
        BigDecimal stopDistancePercent,
        BigDecimal firstTargetPercent,
        String exchangeUrl) {

    /**
     * 빠진 값을 기본값으로 채운다.
     *
     * <p>{@code orDefault} 로 뽑은 이유는 Checkstyle 의 순환 복잡도 한계(8) 때문만이 아니다 —
     * 삼항 연산자 여덟 개가 늘어서면 <b>어느 칸이 무엇으로 채워지는지 읽히지 않는다.</b>
     */
    public TradingProperties {
        mode = orDefault(mode, TradingMode.PAPER);
        cycle = orDefault(cycle, Duration.ofMinutes(1));
        candles = orDefault(candles);
        equity = orDefault(equity, new BigDecimal("800"));
        takerFeePercent = orDefault(takerFeePercent, new BigDecimal("0.05"));
        slippagePercent = orDefault(slippagePercent, new BigDecimal("0.02"));
        strategy = orDefault(blankToNull(strategy), "hold");
        stopDistancePercent = orDefault(stopDistancePercent, new BigDecimal("2"));
        firstTargetPercent = orDefault(firstTargetPercent, new BigDecimal("2"));
        exchangeUrl = orDefault(blankToNull(exchangeUrl), "https://testnet.binancefuture.com");
    }

    public Money startingEquity() {
        return Money.of(equity);
    }

    public Percentage takerFee() {
        return Percentage.of(takerFeePercent);
    }

    public Percentage slippage() {
        return Percentage.of(slippagePercent);
    }

    /** 손절 거리. <b>근거 있는 수가 아니라 자리표시자다</b> — 이 저장소가 잰 적이 없다. */
    public Percentage stopDistance() {
        return Percentage.of(stopDistancePercent);
    }

    /** 1차 익절 거리. 같은 자리표시자다. */
    public Percentage firstTarget() {
        return Percentage.of(firstTargetPercent);
    }

    /**
     * 왕복 비용. 본전 손절이 <b>진입가가 아니라</b> 이만큼 유리한 쪽에 놓인다 —
     * 진입가에 걸면 수수료만큼 지고 끝난다.
     */
    public Percentage roundTripCost() {
        return Percentage.of(takerFeePercent.multiply(new java.math.BigDecimal("2"))
                .add(slippagePercent.multiply(new java.math.BigDecimal("2"))));
    }

    private static <T> T orDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** 봉 수는 {@code int} 라 {@code null} 이 없다. 0 이하가 "안 적었다" 는 뜻이다. */
    private static int orDefault(int candles) {
        return candles <= 0 ? 300 : candles;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
