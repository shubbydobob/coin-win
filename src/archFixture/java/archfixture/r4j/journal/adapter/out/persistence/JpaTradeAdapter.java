package archfixture.r4j.journal.adapter.out.persistence;

/** 규칙 4 픽스처의 대상. journal.application 이 이 어댑터를 직접 참조하면 헥사고날이 무너진다. */
public class JpaTradeAdapter {
    public String loadTrades() {
        return "trades";
    }
}
