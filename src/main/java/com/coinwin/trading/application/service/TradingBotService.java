package com.coinwin.trading.application.service;

import com.coinwin.market.domain.Symbol;
import com.coinwin.trading.application.port.in.RunTradingCycleUseCase;
import com.coinwin.trading.application.port.out.LoadBotContextPort;
import com.coinwin.trading.domain.BotContext;
import com.coinwin.trading.domain.PlacedOrder;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.RiskVerdict;
import com.coinwin.trading.domain.TradingCycle;
import com.coinwin.trading.domain.TradingStrategy;
import com.coinwin.trading.application.port.out.PlaceOrderPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 봇의 한 사이클. <b>조율만 하고 판단하지 않는다.</b>
 *
 * <p>순서가 이 클래스의 전부다 — 한 번에 읽고 · 전략에게 묻고 · 안전장치에 통과시키고 ·
 * 남은 것만 낸다. 어느 칸도 건너뛸 수 없게 타입이 막는다: 전략은 {@code OrderIntent} 만
 * 만들고, 브로커는 그것을 받지만, 그 사이에 {@link RiskVerdict} 가 있다.
 *
 * <p><b>던지지 않는다.</b> 자동으로 도는 루프에서 예외가 올라가면 다음 사이클이 돌지 안 돌지가
 * 스케줄러의 사정이 된다. 실패는 사이클 기록에 이유로 담기고, <b>그러면 실패도 기록에 남는다.</b>
 */
@Service
public class TradingBotService implements RunTradingCycleUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TradingBotService.class);
    private static final Symbol SYMBOL = Symbol.BTC_USDT;

    private final TradingStrategy strategy;
    private final PlaceOrderPort broker;
    private final LoadBotContextPort context;
    private final RiskLimits limits;

    public TradingBotService(
            TradingStrategy strategy, PlaceOrderPort broker,
            LoadBotContextPort context, RiskLimits limits) {
        this.strategy = strategy;
        this.broker = broker;
        this.context = context;
        this.limits = limits;
    }

    @Override
    public TradingCycle runOnce() {
        try {
            return cycle(context.contextFor(SYMBOL));
        } catch (RuntimeException e) {
            // 원인은 로그에만 남긴다. 사이클 기록은 사람이 읽는 자리라 한 문장이면 된다.
            LOG.warn("사이클이 실패했다 — {} 모드", broker.mode(), e);
            return TradingCycle.halted(failedAt(), broker.mode(), "읽지 못했다: " + e.getMessage());
        }
    }

    /**
     * 읽은 것 위에서 한 사이클.
     *
     * <p><b>누적 손실 한계는 전략에게 묻기도 전에 본다.</b> 물어 놓고 전부 거부하면 기록이
     * "전략이 내려 했는데 막혔다" 로 읽히는데, 사실은 봇이 꺼져 있어야 하는 상태다.
     */
    private TradingCycle cycle(BotContext now) {
        if (limits.halts(now.account())) {
            return TradingCycle.halted(now.view().at(), broker.mode(),
                    "누적 손실 한계를 넘었다. 사람이 켜야 다시 돈다");
        }
        List<RiskVerdict> judged = strategy.decide(now.view(), now.open()).stream()
                .map(intent -> limits.judge(intent, now.account()))
                .toList();
        List<PlacedOrder> placed = judged.stream()
                .filter(RiskVerdict::allowed)
                .map(verdict -> broker.place(verdict.intent()))
                .toList();
        return new TradingCycle(now.view().at(), broker.mode(), strategy.name(),
                judged, placed, Optional.empty());
    }

    /**
     * 실패한 사이클의 시각은 <b>우리 시계</b>다. 거래소를 못 읽었으므로 그쪽 시각이 없고,
     * 없는 것을 지어내는 것보다 어느 시계인지 적어 두는 쪽이 낫다.
     */
    private static Instant failedAt() {
        return Instant.now();
    }
}
