package com.coinwin.market.application.port.out;

import com.coinwin.common.domain.ExchangeRate;
import java.util.Optional;

/**
 * USDT 를 원화로 옮길 환율을 읽어 오는 곳.
 *
 * <p><b>비어 있음을 예외가 아니라 값으로 낸다.</b> 다른 포트들은 못 읽으면 던지지만 여기는
 * 다르다 — 환율은 계산에 쓰이지 않고 <b>결과를 원화로도 보여 주기 위한 곁들임</b>이라,
 * 이것 때문에 복리 계산 전체가 실패하면 안 된다. 대신 없는 것은 없는 대로 드러난다:
 * 0 원으로 채우지 않는다.
 */
public interface LoadExchangeRatePort {

    /** 지금 환율. 거래소에 닿지 못했거나 응답이 비어 있으면 {@code Optional.empty()}. */
    Optional<ExchangeRate> wonPerUsdt();
}
