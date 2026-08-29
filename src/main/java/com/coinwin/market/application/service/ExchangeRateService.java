package com.coinwin.market.application.service;

import com.coinwin.common.domain.ExchangeRate;
import com.coinwin.market.application.port.in.LoadExchangeRateUseCase;
import com.coinwin.market.application.port.out.LoadExchangeRatePort;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 환율을 그대로 통과시킨다.
 *
 * <p>캐시를 두지 않는다. 사용자 한 명이 계산 버튼을 누를 때만 부르므로 초당 요청이 될 수
 * 없고, 캐시를 두면 <b>화면이 말하는 "잰 시각" 이 거짓이 된다.</b>
 *
 * <p><b>기본 환율을 두지 않는다.</b> 못 읽었을 때 그럴듯한 수로 채우면 화면이 옛날 환율로
 * 환산한 금액을 지금 값처럼 띄우고, 사람은 그것을 알 방법이 없다.
 */
@Service
public class ExchangeRateService implements LoadExchangeRateUseCase {

    private final LoadExchangeRatePort port;

    public ExchangeRateService(LoadExchangeRatePort port) {
        this.port = port;
    }

    @Override
    public Optional<ExchangeRate> wonPerUsdt() {
        return port.wonPerUsdt();
    }
}
