package com.coinwin.market.adapter.out.memory;

import com.coinwin.common.domain.Price;
import com.coinwin.market.application.port.out.LoadMacroQuotesPort;
import com.coinwin.market.domain.MacroQuote;
import com.coinwin.market.domain.MacroWatchlist;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 거시 시세를 메모리에 담는 어댑터. 거래소 없이 화면 테스트를 돌리기 위해 있다. */
public class InMemoryMacroQuoteAdapter implements LoadMacroQuotesPort {

    private final List<MacroQuote> quotes = new CopyOnWriteArrayList<>();

    /** 관심 목록 다섯을 그럴듯한 값으로 채운다. 오른 것과 내린 것을 섞는다. */
    public static InMemoryMacroQuoteAdapter withSample() {
        InMemoryMacroQuoteAdapter adapter = new InMemoryMacroQuoteAdapter();
        // 관심 목록의 길이에 맞춰 돈다. 상수 배열로 두면 목록이 늘어난 날 여기서 터진다 —
        // 실제로 다섯에서 열둘로 늘리면서 그 일이 났다.
        BigDecimal[] changes = {
            new BigDecimal("0.84"), new BigDecimal("-0.31"), new BigDecimal("1.72"),
            new BigDecimal("-0.55"), new BigDecimal("2.90"),
        };
        int index = 0;
        for (var symbol : MacroWatchlist.ordered()) {
            adapter.quotes.add(new MacroQuote(
                    symbol,
                    MacroWatchlist.labelOf(symbol),
                    MacroWatchlist.groupOf(symbol),
                    Price.of(BigDecimal.valueOf(100 + index * 37L)),
                    changes[index % changes.length]));
            index++;
        }
        return adapter;
    }

    @Override
    public List<MacroQuote> quotes() {
        return List.copyOf(quotes);
    }
}
