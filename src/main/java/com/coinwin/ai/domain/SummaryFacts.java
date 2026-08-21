package com.coinwin.ai.domain;

import com.coinwin.common.domain.DomainValues;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 요약이 쓸 수 있는 사실 전부. <b>여기 없는 수는 지어낸 수다.</b>
 *
 * <p>백테스트를 모른다. 어떤 모듈이든 자기 수치를 이 모양으로 담아 넘기면 요약을 받을 수
 * 있다. {@code ai} 가 {@code backtest} 를 참조하지 않는 것은 취향이 아니라 필요다 —
 * 백테스트 쪽이 요약을 부르는데 이쪽이 백테스트를 알면 모듈 순환이 되고 ArchUnit 규칙 3 이
 * 빌드를 세운다.
 *
 * @param numbers 대조 대상. 요약에 나오는 수는 전부 이 값들로 설명돼야 한다
 * @param context 수가 아닌 사실. 종목·주기·구간처럼 문장에 필요하지만 대조할 것이 없는 것들
 */
public record SummaryFacts(Map<String, BigDecimal> numbers, Map<String, String> context) {

    public SummaryFacts {
        DomainValues.required(numbers, "요약이 쓸 수치");
        DomainValues.required(context, "요약이 쓸 맥락");
        // Map.copyOf 를 쓰지 않는다 — 순서를 잃는다. 프롬프트에 넣는 순서가 곧 읽는 순서다.
        numbers = Collections.unmodifiableMap(new LinkedHashMap<>(numbers));
        context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * 요약에 쓰인 수 하나가 이 사실들로 설명되는가.
     *
     * <p>반올림을 허용한다. 12.4567 을 "12.5" 로 쓰는 것은 지어내는 것이 아니라 읽을 수 있게
     * 쓰는 것이다. 판정은 <b>쓰인 자릿수로 원본을 반올림해 같은지</b> 보는 것이므로, 반올림해도
     * 원본이 되지 않는 수는 그대로 걸린다.
     */
    public boolean explains(BigDecimal written) {
        return numbers.values().stream().anyMatch(fact -> roundsTo(fact, written));
    }

    /** 프롬프트에 넣을 모양. 모델이 보는 수는 이것이 전부다. */
    public String rendered() {
        return Stream.concat(
                        context.entrySet().stream().map(SummaryFacts::line),
                        numbers.entrySet().stream().map(SummaryFacts::line))
                .collect(Collectors.joining("\n"));
    }

    private static boolean roundsTo(BigDecimal fact, BigDecimal written) {
        return fact.setScale(written.scale(), RoundingMode.HALF_UP).compareTo(written) == 0;
    }

    private static String line(Map.Entry<String, ?> entry) {
        Object value = entry.getValue();
        String text = value instanceof BigDecimal number ? number.toPlainString() : value.toString();
        return "- %s: %s".formatted(entry.getKey(), text);
    }
}
