package com.coinwin.market.application.port.in;

import com.coinwin.common.domain.ExchangeRate;
import java.util.Optional;

/** USDT 하나가 지금 몇 원인가. 없을 수 있다. */
public interface LoadExchangeRateUseCase {

    Optional<ExchangeRate> wonPerUsdt();
}
