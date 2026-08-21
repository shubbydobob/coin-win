package com.coinwin.ai.domain;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.util.List;

/**
 * 답변이 검색되지 않은 거래를 근거로 들었다.
 *
 * <p>이것을 통과시키면 <b>존재하지 않는 기록을 인용하는 답</b>이 나간다. 사용자는 인용된
 * 식별자를 믿고 그 거래를 찾으려 할 것이고, 찾지 못하면 그때부터 모든 답을 의심해야 한다.
 *
 * <p>503 인 이유는 {@code FabricatedNumberException} 과 같다 — 검색 결과 밖의 것을 인용한 것은
 * 질문이 잘못된 것이 아니라 모델이 규칙을 어긴 것이고, 사용자가 고칠 것이 없다.
 */
public class UnknownCitationException extends ExternalDataUnavailableException {

    private static final long serialVersionUID = 1L;

    public UnknownCitationException(List<String> unknown) {
        super("답변이 검색되지 않은 거래를 근거로 들었다: %s".formatted(String.join(", ", unknown)));
    }
}
