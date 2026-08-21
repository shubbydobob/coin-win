package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.InvalidValueException;
import java.util.List;
import java.util.Set;

/**
 * 매매 기록에 대한 답변과 <b>그 근거</b>.
 *
 * <p>인용된 식별자는 검색된 거래 안에 있어야 한다. 없는 것을 인용한 답이 나가면 사용자는 그
 * 거래를 찾으려 할 것이고, 찾지 못하는 순간부터 모든 답을 의심해야 한다.
 *
 * <p>근거를 함께 내려 주는 것이 이 기능의 안전장치 전부다. 요약이 원본 수치와 대조되는 것과
 * 같은 자리다 — 검증할 수 없는 답은 이 프로젝트가 하지 않기로 한 것이다({@code docs/adr/005}).
 */
public record JournalAnswer(String text, List<String> citedTradeIds,
        List<RetrievedTrade> retrieved) {

    public JournalAnswer {
        DomainValues.required(citedTradeIds, "근거 거래 목록");
        DomainValues.required(retrieved, "검색된 거래 목록");
        text = requireText(text);
        citedTradeIds = List.copyOf(citedTradeIds);
        retrieved = List.copyOf(retrieved);
        List<String> unknown = unknownAmong(citedTradeIds, retrieved);
        if (!unknown.isEmpty()) {
            throw new UnknownCitationException(unknown);
        }
    }

    /**
     * 검색 결과가 없을 때의 답. <b>모델을 부르지 않는다</b> — 없는 것에 대해 문장을 만들 기회
     * 자체를 주지 않는 것이 환각을 막는 가장 확실한 방법이다.
     */
    public static JournalAnswer nothingFound() {
        return new JournalAnswer("해당하는 기록이 없다.", List.of(), List.of());
    }

    private static List<String> unknownAmong(
            List<String> cited, List<RetrievedTrade> retrieved) {
        Set<String> known = retrieved.stream().map(RetrievedTrade::tradeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return cited.stream().filter(id -> !known.contains(id)).toList();
    }

    private static String requireText(String text) {
        String stripped = DomainValues.required(text, "답변").strip();
        if (stripped.isEmpty()) {
            throw new InvalidValueException("답변은(는) 비어 있을 수 없다");
        }
        return stripped;
    }
}
