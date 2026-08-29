package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadMacroQuotesUseCase;
import com.coinwin.market.application.port.out.LoadMacroQuotesPort;
import com.coinwin.market.domain.MacroQuote;
import com.coinwin.market.domain.MacroWatchlist;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 거시 시세를 그대로 통과시킨다.
 *
 * <p>관심 목록은 {@code MacroWatchlist} 가 갖는다 — 서비스가 종목을 정하면 "왜 이 다섯인가"
 * 가 코드에서 사라진다.
 *
 * <p><b>상관관계를 계산하지 않는다.</b> 이 층에 계산이 하나도 없는 것이 그 규칙의 모습이다.
 *
 * <p><b>출처가 둘이 되면서 합치는 자리가 생겼다.</b> 대부분은 바이낸스에 있고 셋(나스닥 선물 ·
 * S&P 500 · 달러지수)만 야후에 있다. 어느 어댑터도 상대를 모르며, 각자 관심 목록에서 자기 몫만
 * 골라 읽는다 — 합치는 것은 조율이지 계산이 아니므로 이 층의 일이다.
 *
 * <p><b>순서는 관심 목록이 정한다.</b> 어댑터가 답하는 순서대로 이어 붙이면 화면 순서가
 * 배선에 달리게 된다.
 */
@Service
public class MacroQuoteService implements LoadMacroQuotesUseCase {

    private final List<LoadMacroQuotesPort> ports;

    public MacroQuoteService(List<LoadMacroQuotesPort> ports) {
        this.ports = List.copyOf(ports);
    }

    @Override
    public List<MacroQuote> macroQuotes() {
        Map<String, MacroQuote> read = ports.stream()
                .flatMap(port -> port.quotes().stream())
                .collect(Collectors.toMap(
                        quote -> quote.ticker().value(), quote -> quote, (first, second) -> first,
                        LinkedHashMap::new));
        // 관심 목록 순서로 다시 세운다. 못 읽은 것은 그냥 빠진다.
        return MacroWatchlist.ordered().stream()
                .map(ticker -> read.get(ticker.value()))
                .filter(Objects::nonNull)
                .toList();
    }
}
