package archfixture.r4j.journal.application.service;

import archfixture.r4j.journal.adapter.out.persistence.JpaTradeAdapter;

/**
 * 규칙 4 위반 — <b>journal 쪽</b>: journal.application 이 journal.adapter 를 직접 참조한다.
 *
 * <p>{@code market} 픽스처(r4)와 따로 두는 이유가 있다. 규칙 4 는 두 모듈 이름을 <b>손으로
 * 적어</b> 열거한다. market 픽스처만 있으면 journal 쪽 패키지 이름에 오타가 나거나 항목이
 * 통째로 빠져도 규칙은 계속 초록이고, 그 사실을 알 방법이 없다.
 *
 * <p>근거: {@code .claude/docs/roadmap.md} Phase 5 — "확인 방법은 journal 위반 픽스처를
 * archfixture 에 추가하는 것이다."
 */
public class LeakyJournalService {
    private final JpaTradeAdapter adapter = new JpaTradeAdapter();

    public String load() {
        return adapter.loadTrades();
    }
}
