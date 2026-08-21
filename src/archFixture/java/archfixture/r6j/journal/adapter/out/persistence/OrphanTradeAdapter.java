package archfixture.r6j.journal.adapter.out.persistence;

/**
 * 규칙 6 위반 — <b>journal 쪽</b>: 이름은 {@code *Adapter} 이고 {@code adapter.out} 에 있지만
 * {@code application.port.out} 의 어떤 인터페이스도 구현하지 않는다.
 *
 * <p>규칙 6 의 패턴은 모듈 이름을 가리지 않으므로({@code ..adapter.out..}) journal 은 자동으로
 * 검사 대상이 된다. 그래도 픽스처를 두는 이유는 <b>자동으로 걸린다는 것 자체를 증명</b>하기
 * 위해서다. Phase 5 에서 journal 어댑터가 실제로 생겼으므로 지금 확인해 두는 것이 맞다.
 */
public class OrphanTradeAdapter {
    public String loadTrades() {
        return "trades";
    }
}
