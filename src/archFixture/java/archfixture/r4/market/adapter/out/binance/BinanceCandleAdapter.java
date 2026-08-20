package archfixture.r4.market.adapter.out.binance;

/** 규칙 4 픽스처의 대상. application 이 이 어댑터를 직접 참조하면 헥사고날이 무너진다. */
public class BinanceCandleAdapter {
    public String fetch() {
        return "candle";
    }
}
