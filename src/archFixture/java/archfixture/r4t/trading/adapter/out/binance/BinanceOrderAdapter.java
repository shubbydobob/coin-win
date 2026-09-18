package archfixture.r4t.trading.adapter.out.binance;

/** 규칙 4 픽스처의 대상. trading.application 이 이 어댑터를 직접 잡으면 안 된다. */
public class BinanceOrderAdapter {
    public String place() {
        return "placed";
    }
}
