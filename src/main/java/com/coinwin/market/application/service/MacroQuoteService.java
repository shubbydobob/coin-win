package com.coinwin.market.application.service;

import com.coinwin.market.application.port.in.LoadMacroQuotesUseCase;
import com.coinwin.market.application.port.out.LoadMacroQuotesPort;
import com.coinwin.market.domain.MacroQuote;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 거시 시세를 그대로 통과시킨다.
 *
 * <p>관심 목록은 {@code MacroWatchlist} 가 갖는다 — 서비스가 종목을 정하면 "왜 이 다섯인가"
 * 가 코드에서 사라진다.
 *
 * <p><b>상관관계를 계산하지 않는다.</b> 이 층에 계산이 하나도 없는 것이 그 규칙의 모습이다.
 */
@Service
public class MacroQuoteService implements LoadMacroQuotesUseCase {

    private final LoadMacroQuotesPort port;

    public MacroQuoteService(LoadMacroQuotesPort port) {
        this.port = port;
    }

    @Override
    public List<MacroQuote> macroQuotes() {
        return port.quotes();
    }
}
