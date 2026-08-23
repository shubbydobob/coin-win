package com.coinwin.market.application.port.out;

import com.coinwin.market.domain.MacroQuote;
import java.util.List;

/**
 * 비트코인 밖의 자산 시세를 읽어 오는 곳.
 *
 * <p>{@link LoadOrderBookPort#tickerFor} 와 같은 엔드포인트를 쓰지만 <b>다른 질문</b>에
 * 답한다. 그쪽은 "지금 이 종목이 얼마인가" 이고 이쪽은 "다른 자산들은 오늘 어땠나" 다 —
 * 목록으로 받아야 뜻이 되고, 하나가 실패했다고 나머지를 못 볼 이유도 없다.
 */
public interface LoadMacroQuotesPort {

    /** 관심 목록의 시세. <b>못 읽은 것은 목록에서 빠진다</b> — 하나가 나머지를 막지 않는다. */
    List<MacroQuote> quotes();
}
