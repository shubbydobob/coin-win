package archfixture.r5.backtest.application;

import archfixture.r5.market.adapter.out.binance.BinanceCandleAdapter;

/** 규칙 5 위반: backtest 가 market.adapter 를 직접 참조한다 (포트가 아니라). */
public class LeakyEngine {
    private final BinanceCandleAdapter adapter = new BinanceCandleAdapter();

    public String run() {
        return adapter.fetch();
    }
}
