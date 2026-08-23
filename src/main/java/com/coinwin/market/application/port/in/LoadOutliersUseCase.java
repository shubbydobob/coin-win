package com.coinwin.market.application.port.in;

import com.coinwin.market.domain.MarketOutliers;
import com.coinwin.market.domain.Symbol;

/** 세 지표가 각각 평소와 얼마나 다른가. */
public interface LoadOutliersUseCase {

    MarketOutliers outliers(Symbol symbol);
}
