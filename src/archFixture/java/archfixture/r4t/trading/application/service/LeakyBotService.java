package archfixture.r4t.trading.application.service;

import archfixture.r4t.trading.adapter.out.binance.BinanceOrderAdapter;

/**
 * 규칙 4 위반 — <b>trading 쪽</b>: trading.application 이 trading.adapter 를 직접 참조한다.
 *
 * <p>모듈마다 픽스처를 두는 이유는 같다. 규칙 4 는 모듈 이름을 <b>손으로 적어</b> 열거하므로,
 * 픽스처가 없으면 항목이 빠지거나 오타가 나도 규칙은 계속 초록이다.
 *
 * <p><b>이 모듈에서 규칙 4 가 가장 날카롭다.</b> {@code account} 에서는 깨지면 서명 키가
 * 샜고, 여기서는 <b>실계좌 브로커에 닿는 경로가 하나 더 생긴다.</b> 그 경로는 포트를 지나지
 * 않으므로 {@code RiskLimits} 도 지나지 않는다 — 전략이 뚫을 수 없어야 할 벽에 문이 하나
 * 열리는 것이고, 자동으로 도는 루프에서 그 문은 사람이 보고 있지 않은 동안 쓰인다.
 * 근거: {@code docs/spec/trading-bot.md} § 2
 */
public class LeakyBotService {
    private final BinanceOrderAdapter adapter = new BinanceOrderAdapter();

    public String run() {
        return adapter.place();
    }
}
