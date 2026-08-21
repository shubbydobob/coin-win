package archfixture.r4.market.application.service;

import archfixture.r4.market.adapter.out.binance.BinanceCandleAdapter;

/** 규칙 4 위반: market.application 이 market.adapter 를 직접 참조한다. */
public class LeakyService {
    private final BinanceCandleAdapter adapter = new BinanceCandleAdapter();

    public String load() {
        return adapter.fetch();
    }
}
