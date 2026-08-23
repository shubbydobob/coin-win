package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.MacroQuote;
import java.util.List;

/** 비트코인 밖의 자산들은 오늘 어땠나. */
public interface LoadMacroQuotesUseCase {

    List<MacroQuote> macroQuotes();
}
