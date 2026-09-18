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
 */
@ConfigurationProperties("coinwin.trading")
public record TradingProperties(
        boolean enabled,
        TradingMode mode,
        Duration cycle,
        int candles,
        BigDecimal equity,
        BigDecimal takerFeePercent,
        BigDecimal slippagePercent) {

    public TradingProperties {
        mode = mode == null ? TradingMode.PAPER : mode;
        cycle = cycle == null ? Duration.ofMinutes(1) : cycle;
        candles = candles <= 0 ? 300 : candles;
        equity = equity == null ? new BigDecimal("800") : equity;
        takerFeePercent = takerFeePercent == null ? new BigDecimal("0.05") : takerFeePercent;
        slippagePercent = slippagePercent == null ? new BigDecimal("0.02") : slippagePercent;
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
}
