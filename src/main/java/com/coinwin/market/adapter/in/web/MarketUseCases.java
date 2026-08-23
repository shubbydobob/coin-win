package com.coinwin.market.adapter.in.web;

import com.coinwin.market.application.port.in.LoadMacroQuotesUseCase;
import com.coinwin.market.application.port.in.LoadMarketDataUseCase;
import com.coinwin.market.application.port.in.LoadMarketMetricsUseCase;
import com.coinwin.market.application.port.in.LoadOrderBookUseCase;
import com.coinwin.market.application.port.in.LoadOutliersUseCase;
import com.coinwin.market.application.port.in.SyncMarketDataUseCase;
import org.springframework.stereotype.Component;

/**
 * {@code market} 의 인바운드 포트 묶음.
 *
 * <p>감시 화면이 유스케이스 둘을 더하면서 컨트롤러 생성자가 다섯 개가 됐고,
 * {@code conventions.md} 의 네 개 한계에 걸렸다. 한계를 늘리는 대신 묶는다 — 그 한계는
 * "파라미터가 늘면 파라미터 객체로 추출한다" 는 처방을 함께 갖고 있다.
 *
 * <p><b>이 묶음은 포트를 감추지 않는다.</b> 컨트롤러는 여전히 다섯 개의 인터페이스를 각각
 * 쓰고, 여기서 새로 정의하는 규칙은 하나도 없다. 파사드를 만들어 메서드를 다시 늘어놓으면
 * 그 순간 "무엇이 어느 유스케이스의 일인가" 가 흐려진다.
 */
@Component
public record MarketUseCases(
        LoadMarketDataUseCase loadMarketData,
        SyncMarketDataUseCase syncMarketData,
        LoadMarketMetricsUseCase loadMetrics,
        LoadOrderBookUseCase loadOrderBook,
        LoadOutliersUseCase loadOutliers,
        LoadMacroQuotesUseCase loadMacroQuotes) {
}
