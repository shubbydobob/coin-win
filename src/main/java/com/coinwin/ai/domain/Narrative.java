package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.math.BigDecimal;
import java.util.List;

/**
 * 수치를 문장으로 바꾼 것. <b>원본에 없는 수를 담고 있으면 존재할 수 없다.</b>
 *
 * <p>ADR 005 가 요약을 허용한 근거가 "원본 수치와 대조할 수 있다" 였다. 그 대조를 생성자에
 * 두는 이유는 검사를 테스트에만 두면 실제 응답이 검증되지 않은 채 나가기 때문이다 —
 * 검증할 수 있어서 허용한 기능인데 검증하지 않으면 허용 근거가 사라진다.
 */
public record Narrative(String text, SummaryFacts facts) {

    public Narrative {
        DomainValues.required(facts, "요약의 원본 수치");
        text = requireText(text);
        List<BigDecimal> fabricated = WrittenNumbers.worthChecking(text).stream()
                .filter(written -> !facts.explains(written))
                .toList();
        if (!fabricated.isEmpty()) {
            throw new FabricatedNumberException(fabricated);
        }
    }

    private static String requireText(String text) {
        String stripped = DomainValues.required(text, "요약 문장").strip();
        if (stripped.isEmpty()) {
            throw new InvalidValueException("요약 문장은(는) 비어 있을 수 없다");
        }
        return stripped;
    }
}
