package com.coinwin.ai.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 요약 문장에서 <b>대조해야 할 수</b>만 골라낸다.
 *
 * <p>전부 대조하면 요약을 쓸 수 없다. "두 가지", "3 번째" 같은 말이 전부 지어낸 수로 걸리기
 * 때문이다. 그래서 <b>소수를 포함하거나 1000 이상인 수</b>만 검사한다 — 가격·손익·자산은
 * 1000 이상이고 승률·손익비·낙폭은 소수다. 지어낼 수 있는 값은 전부 이 그물에 걸린다.
 *
 * <p>남는 구멍이 하나 있다. "약 6만" 처럼 한국어 단위로 쓰면 토큰은 {@code 6} 하나라 검사를
 * 빠져나간다. 프롬프트가 수를 그대로 쓰게 하고 있으므로 지금은 열어 둔다.
 */
final class WrittenNumbers {

    /** 이 미만의 정수는 가격으로 오인될 수 없다. */
    private static final BigDecimal SMALL = new BigDecimal("1000");

    /** ISO 날짜의 연도를 지어낸 가격으로 읽으면 구간을 언급하는 순간 모든 요약이 거절된다. */
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}(T[\\d:.]+Z?)?");

    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,]*(\\.\\d+)?");

    private WrittenNumbers() {
    }

    static List<BigDecimal> worthChecking(String text) {
        return NUMBER.matcher(DATE.matcher(text).replaceAll(" "))
                .results()
                .map(match -> new BigDecimal(match.group().replace(",", "")))
                .filter(WrittenNumbers::couldBeFabricated)
                .toList();
    }

    private static boolean couldBeFabricated(BigDecimal number) {
        return number.scale() > 0 || number.abs().compareTo(SMALL) >= 0;
    }
}
