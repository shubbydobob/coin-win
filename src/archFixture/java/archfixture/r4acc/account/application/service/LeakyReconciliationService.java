package archfixture.r4acc.account.application.service;

import archfixture.r4acc.account.adapter.out.binance.BinancePositionAdapter;

/**
 * 규칙 4 위반 — <b>account 쪽</b>: account.application 이 account.adapter 를 직접 참조한다.
 *
 * <p>{@code market}(r4) · {@code journal}(r4j) · {@code ai}(r4a) 와 따로 두는 이유는 같다.
 * 규칙 4 는 모듈 이름을 <b>손으로 적어</b> 열거하므로, 모듈마다 픽스처가 없으면 항목이 빠지거나
 * 오타가 나도 규칙은 계속 초록이다.
 *
 * <p>account 에서 이 규칙이 특히 중요한 이유가 있다. 서비스가 어댑터를 직접 잡으면
 * <b>서명 키가 application 으로 샌다.</b> 그 순간 "키 없이 도는 서비스 테스트" 가 성립하지
 * 않고, 시크릿이 흐르는 코드 범위가 넓어진다.
 * 근거: {@code docs/spec/phase9-exchange-positions.md} § 3
 */
public class LeakyReconciliationService {
    private final BinancePositionAdapter adapter = new BinancePositionAdapter();

    public String reconcile() {
        return adapter.positions();
    }
}
