package com.coinwin.trading.adapter.in.schedule;

import com.coinwin.trading.application.port.in.RunTradingCycleUseCase;
import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.domain.TradingCycle;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 봇을 깨우는 것. <b>"자는 동안" 이 성립하는 자리가 여기 하나다.</b>
 *
 * <p><b>사이클이 겹치지 않는다.</b> 앞 사이클이 안 끝났으면 건너뛰고 그 사실을 남긴다.
 * 겹치면 같은 신호로 주문이 두 번 나가고, 스프링의 기본 스케줄러는 단일 스레드지만 그
 * 보장에 기대지 않는다 — 풀 크기 설정 한 줄이 바뀌는 순간 조용히 깨진다.
 *
 * <p><b>꺼져 있으면 아무것도 하지 않는다.</b> 열린 포지션도 건드리지 않는다 — 봇을 끄는 것과
 * 포지션을 닫는 것은 다른 결정이고, 끄는 순간 전량 시장가로 닫으면 그 자체가 사람이 원하지
 * 않은 매매다.
 *
 * <p><b>조용한 사이클은 로그를 남기지 않는다.</b> 대부분의 사이클이 아무 일도 하지 않으므로
 * 전부 찍으면 진짜 사건이 그 안에 묻힌다 — {@code DomainExceptionHandler} 가 원인 없는
 * 예외를 남기지 않는 것과 같은 판단이다.
 */
@Component
public class TradingBotScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TradingBotScheduler.class);

    private final RunTradingCycleUseCase bot;
    private final TradingProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public TradingBotScheduler(RunTradingCycleUseCase bot, TradingProperties properties) {
        this.bot = bot;
        this.properties = properties;
    }

    /**
     * 주기마다 한 번. 고정 지연이라 <b>사이클이 끝난 뒤부터</b> 다음 주기를 센다 — 고정
     * 간격으로 두면 느린 사이클이 다음 것을 밀어내며 쌓인다.
     */
    @Scheduled(fixedDelayString = "${coinwin.trading.cycle:PT1M}")
    public void tick() {
        if (!properties.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            LOG.warn("앞 사이클이 아직 돌고 있다 — 이번 차례를 건너뛴다");
            return;
        }
        try {
            report(bot.runOnce());
        } finally {
            running.set(false);
        }
    }

    /** 무슨 일이 있었을 때만 남긴다. */
    private static void report(TradingCycle cycle) {
        if (cycle.quiet()) {
            return;
        }
        cycle.halted().ifPresent(reason -> LOG.warn("[{}] 멈췄다 — {}", cycle.mode(), reason));
        cycle.rejected().forEach(rejected ->
                LOG.info("[{}] 막았다 — {}", cycle.mode(), rejected.reason()));
        cycle.placed().forEach(order ->
                LOG.info("[{}] 냈다 — {} {} {}",
                        cycle.mode(), order.intent().position(), order.intent().kind(), order.id()));
    }
}
