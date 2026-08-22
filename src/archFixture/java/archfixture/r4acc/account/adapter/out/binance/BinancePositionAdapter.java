package archfixture.r4acc.account.adapter.out.binance;

/** 규칙 4 픽스처의 대상. account.application 이 이 어댑터를 직접 잡으면 헥사고날이 무너진다. */
public class BinancePositionAdapter {
    public String positions() {
        return "positions";
    }
}
