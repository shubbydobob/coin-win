package archfixture.r6.market.adapter.out.persistence;

/**
 * 규칙 6 위반: 이름은 *Adapter 이고 adapter.out 에 있지만
 * application.port.out 의 어떤 인터페이스도 구현하지 않는다.
 *
 * <p>이런 클래스가 늘어나면 포트/어댑터가 이름만 남는다.
 */
public class OrphanAdapter {
    public String loadCandles() {
        return "candles";
    }
}
