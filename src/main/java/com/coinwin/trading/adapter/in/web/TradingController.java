package com.coinwin.trading.adapter.in.web;

import com.coinwin.trading.adapter.out.TradingProperties;
import com.coinwin.trading.application.port.in.RunTradingCycleUseCase;
import com.coinwin.trading.domain.RiskLimits;
import com.coinwin.trading.domain.TradingStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 봇을 들여다보는 자리.
 *
 * <p><b>여기서 주문을 직접 내지 않는다.</b> 화면이 "롱 0.1 을 사라" 를 보낼 수 있으면 안전
 * 장치와 전략을 둘 다 건너뛰는 경로가 생긴다 — 이 모듈의 모든 주문은 한 사이클을 지나야 한다.
 *
 * <p><b>끄고 켜는 것도 여기에 없다.</b> 설정으로만 바뀐다. 화면에 스위치를 두면 급할 때
 * 손이 닿는 자리에 놓이게 되고, 그 자리가 정확히 규칙을 무력화하는 자리다.
 */
@RestController
@RequestMapping("/api/trading")
@Tag(name = "매매 봇", description = "봇의 상태와 사이클. 주문을 직접 내는 엔드포인트는 없다")
public class TradingController {

    private final RunTradingCycleUseCase bot;
    private final TradingProperties properties;
    private final TradingStrategy strategy;
    private final RiskLimits limits;

    public TradingController(
            RunTradingCycleUseCase bot, TradingProperties properties,
            TradingStrategy strategy, RiskLimits limits) {
        this.bot = bot;
        this.properties = properties;
        this.strategy = strategy;
        this.limits = limits;
    }

    @Operation(
            summary = "봇의 상태와 한계",
            description = """
                    어느 모드로 도는지, 무엇이 판단하는지, 한계가 얼마인지.

                    모드가 첫 칸인 이유는 어느 모드로 돌고 있는지 모르는 상태가 존재하면
                    안 되기 때문이다 — 장부인 줄 알았는데 실계좌인 것이 가장 나쁜 고장이다.

                    한계 기본값은 근거 있는 수가 아니라 자리표시자다.""")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "봇의 상태"))
    @GetMapping("/status")
    public TradingStatusResponse status() {
        return TradingStatusResponse.of(properties, strategy, limits);
    }

    @Operation(
            summary = "사이클을 지금 한 번 돌린다",
            description = """
                    스케줄러를 기다리지 않고 한 번 깨운다. 읽고 · 전략에게 묻고 ·
                    안전장치에 통과시키고 · 남은 것을 낸다.

                    **봇이 꺼져 있어도 돈다.** 끄는 것은 스스로 깨어나지 않게 하는 것이고,
                    사람이 한 번 돌려 보는 것은 그것과 다른 결정이다. 모드는 그대로 지켜지므로
                    장부 모드에서는 여기서도 돈이 움직이지 않는다.

                    던지지 않는다 — 거래소를 못 읽어도 그 사실이 사이클에 담겨 200 으로 온다.""")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "그 사이클이 한 일"))
    @PostMapping("/cycle")
    public TradingCycleResponse runCycle() {
        return TradingCycleResponse.from(bot.runOnce());
    }
}
