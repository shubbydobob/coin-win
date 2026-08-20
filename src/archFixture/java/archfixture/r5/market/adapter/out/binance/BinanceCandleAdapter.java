package archfixture.r5.market.adapter.out.binance;

/** 규칙 5 픽스처의 대상. backtest 가 이 어댑터를 참조하면 백테스트에 거래소 코드가 새어든다. */
public class BinanceCandleAdapter {
    public String fetch() {
        return "candle";
    }
}
